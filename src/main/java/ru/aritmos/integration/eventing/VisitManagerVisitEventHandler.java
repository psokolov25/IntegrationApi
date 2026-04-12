package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerBranchStateEventMapper;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerVisitEventPayload;
import ru.aritmos.integration.service.GatewayService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Обновляет кэш branch-state при событиях визитов VisitManager (VISIT_*).
 */
@Singleton
public class VisitManagerVisitEventHandler implements EventHandler {

    private static final String VISIT_EVENT_PREFIX = "VISIT_";

    private final GatewayService gatewayService;
    private final VisitManagerBranchStateEventMapper mapper;
    private final IntegrationGatewayConfiguration configuration;
    private final Clock clock;
    private final Duration trackingRetention;
    private final AtomicLong nextCleanupAtEpochMillis;
    private final Map<String, TrackingState> trackingByBranchTarget = new ConcurrentHashMap<>();

    public VisitManagerVisitEventHandler(GatewayService gatewayService,
                                         VisitManagerBranchStateEventMapper mapper,
                                         IntegrationGatewayConfiguration configuration) {
        this(gatewayService, mapper, configuration, Clock.systemUTC());
    }

    VisitManagerVisitEventHandler(GatewayService gatewayService,
                                  VisitManagerBranchStateEventMapper mapper,
                                  IntegrationGatewayConfiguration configuration,
                                  Clock clock) {
        this.gatewayService = gatewayService;
        this.mapper = mapper;
        this.configuration = configuration;
        this.clock = clock;
        this.trackingRetention = resolveTrackingRetention(configuration.getBranchStateEventRefreshDebounce());
        this.nextCleanupAtEpochMillis = new AtomicLong(
                Instant.now(clock).plus(this.trackingRetention).toEpochMilli()
        );
    }

    @Override
    public boolean supports(String eventType) {
        return eventType != null && eventType.toUpperCase().startsWith(VISIT_EVENT_PREFIX);
    }

    @Override
    public void handle(IntegrationEvent event) {
        cleanupStaleTracking();
        VisitManagerVisitEventPayload payload = mapper.mapVisitEvent(event);
        String key = payload.sourceVisitManagerId() + ":" + payload.branchId();
        Instant eventTime = event.occurredAt() == null ? Instant.now(clock) : event.occurredAt();
        TrackingState state = trackingByBranchTarget.get(key);
        if (state != null && eventTime.isBefore(state.lastEventTime())) {
            return;
        }
        Instant now = Instant.now(clock);
        if (state != null
                && now.isBefore(state.lastRefreshTime().plus(configuration.getBranchStateEventRefreshDebounce()))) {
            trackingByBranchTarget.put(key, new TrackingState(eventTime, state.lastRefreshTime()));
            return;
        }
        gatewayService.refreshBranchState(
                "eventing-databus",
                payload.branchId(),
                payload.sourceVisitManagerId()
        );
        trackingByBranchTarget.put(key, new TrackingState(eventTime, now));
    }

    private void cleanupStaleTracking() {
        long nowMillis = Instant.now(clock).toEpochMilli();
        long plannedCleanupAt = nextCleanupAtEpochMillis.get();
        if (nowMillis < plannedCleanupAt) {
            return;
        }

        long nextCleanupAt = nowMillis + trackingRetention.toMillis();
        if (!nextCleanupAtEpochMillis.compareAndSet(plannedCleanupAt, nextCleanupAt)) {
            return;
        }

        Instant cutoff = Instant.ofEpochMilli(nowMillis).minus(trackingRetention);
        trackingByBranchTarget.entrySet().removeIf(entry -> entry.getValue().lastTouchTime().isBefore(cutoff));
    }

    private Duration resolveTrackingRetention(Duration debounce) {
        Duration normalizedDebounce = debounce == null || debounce.isNegative() || debounce.isZero()
                ? Duration.ofSeconds(1)
                : debounce;
        Duration retentionByDebounce = normalizedDebounce.multipliedBy(10);
        Duration minRetention = Duration.ofMinutes(1);
        return retentionByDebounce.compareTo(minRetention) < 0 ? minRetention : retentionByDebounce;
    }

    private record TrackingState(Instant lastEventTime, Instant lastRefreshTime) {
        private Instant lastTouchTime() {
            return lastEventTime.isAfter(lastRefreshTime) ? lastEventTime : lastRefreshTime;
        }
    }
}
