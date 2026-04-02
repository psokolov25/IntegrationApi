package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerBranchStateEventMapper;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerBranchStateEventPayload;
import ru.aritmos.integration.service.GatewayService;

/**
 * Обновляет in-memory кэш состояния отделений по событию branch-state-updated.
 */
@Singleton
public class BranchStateUpdatedEventHandler implements EventHandler {

    private final GatewayService gatewayService;
    private final VisitManagerBranchStateEventMapper mapper;

    public BranchStateUpdatedEventHandler(GatewayService gatewayService,
                                          VisitManagerBranchStateEventMapper mapper) {
        this.gatewayService = gatewayService;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String eventType) {
        return "branch-state-updated".equalsIgnoreCase(eventType);
    }

    @Override
    public void handle(IntegrationEvent event) {
        VisitManagerBranchStateEventPayload statePayload = mapper.map(event);

        gatewayService.applyEventBranchState(new BranchStateDto(
                statePayload.branchId(),
                statePayload.sourceVisitManagerId(),
                statePayload.status(),
                statePayload.activeWindow(),
                statePayload.queueSize(),
                statePayload.updatedAt(),
                false,
                statePayload.updatedBy()
        ));
    }
}
