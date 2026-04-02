package ru.suo.integration.client;

import jakarta.inject.Singleton;
import ru.suo.integration.config.IntegrationGatewayConfiguration;
import ru.suo.integration.domain.CallVisitorRequest;
import ru.suo.integration.domain.CallVisitorResponse;
import ru.suo.integration.domain.QueueItemDto;

import java.util.List;
import java.util.UUID;

/**
 * Временный клиент-заглушка для этапов 1-2.
 */
@Singleton
public class StubVisitManagerClient implements VisitManagerClient {

    private final IntegrationGatewayConfiguration configuration;

    public StubVisitManagerClient(IntegrationGatewayConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public List<QueueItemDto> getQueues(String targetVisitManagerId, String branchId) {
        assertAvailable(targetVisitManagerId);
        return List.of(
                new QueueItemDto(branchId + "-Q1", "Основная очередь", 4),
                new QueueItemDto(branchId + "-Q2", "Приоритетная очередь", 1)
        );
    }

    @Override
    public CallVisitorResponse callVisitor(String targetVisitManagerId, String visitorId, CallVisitorRequest request) {
        assertAvailable(targetVisitManagerId);
        return new CallVisitorResponse(UUID.randomUUID().toString(), "CALLED", targetVisitManagerId);
    }

    private void assertAvailable(String targetVisitManagerId) {
        if (configuration.getSimulatedUnavailableTargets().contains(targetVisitManagerId)) {
            throw new IllegalStateException("Target " + targetVisitManagerId + " временно недоступен");
        }
    }
}
