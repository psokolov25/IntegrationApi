package ru.suo.integration.client.policy;

import io.micronaut.context.annotation.Primary;
import jakarta.inject.Singleton;
import ru.suo.integration.client.StubVisitManagerClient;
import ru.suo.integration.client.VisitManagerClient;
import ru.suo.integration.domain.CallVisitorRequest;
import ru.suo.integration.domain.CallVisitorResponse;
import ru.suo.integration.domain.QueueItemDto;

import java.util.List;

/**
 * Обертка над VisitManagerClient с retry/timeout/circuit policies.
 */
@Singleton
@Primary
public class PolicyAwareVisitManagerClient implements VisitManagerClient {

    private final StubVisitManagerClient delegate;
    private final ClientExecutionTemplate executionTemplate;

    public PolicyAwareVisitManagerClient(StubVisitManagerClient delegate, ClientExecutionTemplate executionTemplate) {
        this.delegate = delegate;
        this.executionTemplate = executionTemplate;
    }

    @Override
    public List<QueueItemDto> getQueues(String targetVisitManagerId, String branchId) {
        return executionTemplate.execute("vm:" + targetVisitManagerId,
                () -> delegate.getQueues(targetVisitManagerId, branchId));
    }

    @Override
    public CallVisitorResponse callVisitor(String targetVisitManagerId, String visitorId, CallVisitorRequest request) {
        return executionTemplate.execute("vm:" + targetVisitManagerId,
                () -> delegate.callVisitor(targetVisitManagerId, visitorId, request));
    }
}
