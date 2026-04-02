package ru.aritmos.integration.client;

import ru.aritmos.integration.domain.CallVisitorRequest;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.domain.BranchStateUpdateRequest;
import ru.aritmos.integration.domain.CallVisitorResponse;
import ru.aritmos.integration.domain.QueueItemDto;

import java.util.List;

/**
 * Абстракция клиента к VisitManager.
 */
public interface VisitManagerClient {

    List<QueueItemDto> getQueues(String targetVisitManagerId, String branchId);

    CallVisitorResponse callVisitor(String targetVisitManagerId, String visitorId, CallVisitorRequest request);

    BranchStateDto getBranchState(String targetVisitManagerId, String branchId);

    BranchStateDto updateBranchState(String targetVisitManagerId, String branchId, BranchStateUpdateRequest request);
}
