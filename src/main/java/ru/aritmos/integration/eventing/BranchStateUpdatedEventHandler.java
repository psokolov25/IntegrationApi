package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerBranchStateEventMapper;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerBranchStateEventPayload;
import ru.aritmos.integration.service.GatewayService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Обновляет in-memory кэш состояния отделений по событиям branch-state-updated и ENTITY_CHANGED(Branch).
 */
@Singleton
public class BranchStateUpdatedEventHandler implements EventHandler {

    private final GatewayService gatewayService;
    private final VisitManagerBranchStateEventMapper mapper;
    private final IntegrationGatewayConfiguration configuration;
    private final Clock clock;
    private final Duration trackingRetention;
    private final AtomicLong nextCleanupAtEpochMillis;
    private final Map<String, TrackingState> trackingByBranchTarget = new ConcurrentHashMap<>();

    public BranchStateUpdatedEventHandler(GatewayService gatewayService,
                                          VisitManagerBranchStateEventMapper mapper,
                                          IntegrationGatewayConfiguration configuration) {
        this(gatewayService, mapper, configuration, Clock.systemUTC());
    }

    BranchStateUpdatedEventHandler(GatewayService gatewayService,
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
        return "branch-state-updated".equalsIgnoreCase(eventType)
                || mapper.supportsBranchEntityChangedEventType(eventType);
    }

    @Override
    public void handle(IntegrationEvent event) {
        cleanupStaleTracking();
        if (mapper.supportsBranchEntityChangedEventType(event.eventType())
                && !mapper.isBranchEntityChanged(event)) {
            return;
        }
        VisitManagerBranchStateEventPayload statePayload = mapper.isBranchEntityChanged(event)
                ? mapper.mapEntityChangedBranch(event)
                : mapper.map(event);

        String key = statePayload.sourceVisitManagerId() + ":" + statePayload.branchId();
        String eventId = statePayload.canonicalEventId() == null ? "" : statePayload.canonicalEventId();
        Instant updatedAt = statePayload.updatedAt() == null ? Instant.now(clock) : statePayload.updatedAt();
        String payloadSignature = buildPayloadSignature(statePayload);
        TrackingState current = trackingByBranchTarget.get(key);
        if (current != null && !eventId.isBlank() && eventId.equals(current.lastEventId())) {
            return;
        }
        if (current != null && updatedAt.isBefore(current.lastUpdatedAt())) {
            return;
        }
        if (current != null
                && updatedAt.equals(current.lastUpdatedAt())
                && payloadSignature.equals(current.lastPayloadSignature())) {
            return;
        }
        Instant now = Instant.now(clock);
        if (current != null
                && now.isBefore(current.lastAppliedAt().plus(configuration.getBranchStateEventRefreshDebounce()))
                && !updatedAt.isAfter(current.lastUpdatedAt())
                && payloadSignature.equals(current.lastPayloadSignature())) {
            trackingByBranchTarget.put(key, new TrackingState(
                    current.lastUpdatedAt(),
                    current.lastAppliedAt(),
                    eventId,
                    payloadSignature
            ));
            return;
        }

        boolean applied = gatewayService.applyEventBranchState(new BranchStateDto(
                statePayload.branchId(),
                statePayload.sourceVisitManagerId(),
                statePayload.status(),
                statePayload.activeWindow(),
                statePayload.queueSize(),
                statePayload.updatedAt(),
                false,
                statePayload.updatedBy()
        ));
        if (applied) {
            trackingByBranchTarget.put(key, new TrackingState(updatedAt, now, eventId, payloadSignature));
        }
    }

    private String buildPayloadSignature(VisitManagerBranchStateEventPayload payload) {
        return String.join("|",
                payload.status() == null ? "" : payload.status(),
                payload.activeWindow() == null ? "" : payload.activeWindow(),
                String.valueOf(payload.queueSize()),
                payload.updatedBy() == null ? "" : payload.updatedBy()
        );
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

    private record TrackingState(
            Instant lastUpdatedAt,
            Instant lastAppliedAt,
            String lastEventId,
            String lastPayloadSignature
    ) {
        private Instant lastTouchTime() {
            return lastUpdatedAt.isAfter(lastAppliedAt) ? lastUpdatedAt : lastAppliedAt;
        }
    }
}
