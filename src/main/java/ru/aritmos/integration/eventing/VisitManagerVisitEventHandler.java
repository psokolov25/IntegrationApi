package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerBranchStateEventMapper;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerVisitEventPayload;
import ru.aritmos.integration.service.GatewayService;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<String, Instant> lastRefreshByBranchTarget = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastEventByBranchTarget = new ConcurrentHashMap<>();

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
    }

    @Override
    public boolean supports(String eventType) {
        return eventType != null && eventType.toUpperCase().startsWith(VISIT_EVENT_PREFIX);
    }

    @Override
    public void handle(IntegrationEvent event) {
        VisitManagerVisitEventPayload payload = mapper.mapVisitEvent(event);
        String key = payload.sourceVisitManagerId() + ":" + payload.branchId();
        Instant eventTime = event.occurredAt() == null ? Instant.now(clock) : event.occurredAt();
        Instant lastEventTime = lastEventByBranchTarget.get(key);
        if (lastEventTime != null && eventTime.isBefore(lastEventTime)) {
            return;
        }
        Instant now = Instant.now(clock);
        Instant lastRefresh = lastRefreshByBranchTarget.get(key);
        if (lastRefresh != null
                && now.isBefore(lastRefresh.plus(configuration.getBranchStateEventRefreshDebounce()))) {
            lastEventByBranchTarget.put(key, eventTime);
            return;
        }
        gatewayService.refreshBranchState(
                "eventing-databus",
                payload.branchId(),
                payload.sourceVisitManagerId()
        );
        lastRefreshByBranchTarget.put(key, now);
        lastEventByBranchTarget.put(key, eventTime);
    }
}
