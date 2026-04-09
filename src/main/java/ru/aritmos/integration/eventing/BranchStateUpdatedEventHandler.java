package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerBranchStateEventMapper;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerBranchStateEventPayload;
import ru.aritmos.integration.service.GatewayService;

/**
 * Обновляет in-memory кэш состояния отделений по событиям branch-state-updated и ENTITY_CHANGED(Branch).
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
        return "branch-state-updated".equalsIgnoreCase(eventType)
                || mapper.supportsBranchEntityChangedEventType(eventType);
    }

    @Override
    public void handle(IntegrationEvent event) {
        if (mapper.supportsBranchEntityChangedEventType(event.eventType())
                && !mapper.isBranchEntityChanged(event)) {
            return;
        }
        VisitManagerBranchStateEventPayload statePayload = mapper.isBranchEntityChanged(event)
                ? mapper.mapEntityChangedBranch(event)
                : mapper.map(event);

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
