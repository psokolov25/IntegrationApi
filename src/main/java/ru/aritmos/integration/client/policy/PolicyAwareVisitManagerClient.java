package ru.aritmos.integration.client.policy;

import io.micronaut.context.annotation.Primary;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import ru.aritmos.integration.client.VisitManagerClient;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.domain.BranchStateUpdateRequest;
import ru.aritmos.integration.domain.CallVisitorRequest;
import ru.aritmos.integration.domain.CallVisitorResponse;
import ru.aritmos.integration.domain.QueueItemDto;

import java.util.List;

/**
 * Обертка над VisitManagerClient с retry/timeout/circuit policies.
 */
@Singleton
@Primary
public class PolicyAwareVisitManagerClient implements VisitManagerClient {

    private final VisitManagerClient delegate;
    private final ClientExecutionTemplate executionTemplate;

    public PolicyAwareVisitManagerClient(@Named("rawVisitManagerClient") VisitManagerClient delegate,
                                         ClientExecutionTemplate executionTemplate) {
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

    @Override
    public BranchStateDto getBranchState(String targetVisitManagerId, String branchId) {
        return executionTemplate.execute("vm:" + targetVisitManagerId,
                () -> delegate.getBranchState(targetVisitManagerId, branchId));
    }

    @Override
    public BranchStateDto updateBranchState(String targetVisitManagerId, String branchId, BranchStateUpdateRequest request) {
        return executionTemplate.execute("vm:" + targetVisitManagerId,
                () -> delegate.updateBranchState(targetVisitManagerId, branchId, request));
    }
}
