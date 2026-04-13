package ru.aritmos.integration.client;

import jakarta.inject.Singleton;
import jakarta.inject.Named;
import io.micronaut.context.annotation.Requires;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.domain.BranchStateUpdateRequest;
import ru.aritmos.integration.domain.CallVisitorRequest;
import ru.aritmos.integration.domain.CallVisitorResponse;
import ru.aritmos.integration.domain.QueueItemDto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Временный клиент-заглушка для этапов 1-2.
 */
@Singleton
@Named("rawVisitManagerClient")
@Requires(property = "integration.visit-manager-client.mode", value = "STUB")
public class StubVisitManagerClient implements VisitManagerClient {

    private final IntegrationGatewayConfiguration configuration;
    private final Map<String, BranchStateDto> branchStates = new ConcurrentHashMap<>();

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

    @Override
    public BranchStateDto getBranchState(String targetVisitManagerId, String branchId) {
        assertAvailable(targetVisitManagerId);
        return branchStates.computeIfAbsent(key(targetVisitManagerId, branchId), key -> defaultState(targetVisitManagerId, branchId));
    }

    @Override
    public BranchStateDto updateBranchState(String targetVisitManagerId, String branchId, BranchStateUpdateRequest request) {
        assertAvailable(targetVisitManagerId);
        BranchStateDto updated = new BranchStateDto(
                branchId,
                targetVisitManagerId,
                request.status(),
                request.activeWindow(),
                request.queueSize(),
                Instant.now(),
                false,
                request.updatedBy()
        );
        branchStates.put(key(targetVisitManagerId, branchId), updated);
        return updated;
    }

    private BranchStateDto defaultState(String targetVisitManagerId, String branchId) {
        return new BranchStateDto(
                branchId,
                targetVisitManagerId,
                "OPEN",
                "08:00-20:00",
                0,
                Instant.now(),
                false,
                "visit-manager-sync"
        );
    }

    private String key(String targetVisitManagerId, String branchId) {
        return targetVisitManagerId + ":" + branchId;
    }

    private void assertAvailable(String targetVisitManagerId) {
        if (configuration.getSimulatedUnavailableTargets().contains(targetVisitManagerId)) {
            throw new IllegalStateException("Target " + targetVisitManagerId + " временно недоступен");
        }
    }
}
