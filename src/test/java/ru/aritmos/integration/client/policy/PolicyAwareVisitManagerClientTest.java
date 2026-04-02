package ru.aritmos.integration.client.policy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.client.VisitManagerClient;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.domain.BranchStateUpdateRequest;
import ru.aritmos.integration.domain.CallVisitorRequest;
import ru.aritmos.integration.domain.CallVisitorResponse;
import ru.aritmos.integration.domain.QueueItemDto;

import java.time.Instant;
import java.util.List;

class PolicyAwareVisitManagerClientTest {

    @Test
    void shouldDelegateAllOperationsThroughPolicyWrapper() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        ClientExecutionTemplate template = new ClientExecutionTemplate(cfg);
        VisitManagerClient delegate = new InMemoryVisitManagerClient();
        PolicyAwareVisitManagerClient client = new PolicyAwareVisitManagerClient(delegate, template);

        var queues = client.getQueues("vm-main", "BR-1");
        Assertions.assertEquals(1, queues.size());

        var call = client.callVisitor("vm-main", "visitor-1",
                new CallVisitorRequest("BR-1", "Q-1", "op-1", "idem-1"));
        Assertions.assertEquals("CALLED", call.status());

        var updated = client.updateBranchState("vm-main", "BR-1",
                new BranchStateUpdateRequest("PAUSED", "09:00-18:00", 3, "console"));
        Assertions.assertEquals("PAUSED", updated.status());

        var loaded = client.getBranchState("vm-main", "BR-1");
        Assertions.assertEquals("PAUSED", loaded.status());
    }

    private static class InMemoryVisitManagerClient implements VisitManagerClient {
        private BranchStateDto state = new BranchStateDto(
                "BR-1", "vm-main", "OPEN", "08:00-20:00", 0, Instant.now(), false, "system");

        @Override
        public List<QueueItemDto> getQueues(String targetVisitManagerId, String branchId) {
            return List.of(new QueueItemDto("Q-1", "Основная", 2));
        }

        @Override
        public CallVisitorResponse callVisitor(String targetVisitManagerId, String visitorId, CallVisitorRequest request) {
            return new CallVisitorResponse(visitorId, "CALLED", targetVisitManagerId);
        }

        @Override
        public BranchStateDto getBranchState(String targetVisitManagerId, String branchId) {
            return state;
        }

        @Override
        public BranchStateDto updateBranchState(String targetVisitManagerId, String branchId, BranchStateUpdateRequest request) {
            state = new BranchStateDto(
                    branchId,
                    targetVisitManagerId,
                    request.status(),
                    request.activeWindow(),
                    request.queueSize(),
                    Instant.now(),
                    false,
                    request.updatedBy()
            );
            return state;
        }
    }
}
