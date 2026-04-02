package ru.suo.integration.client;

import ru.suo.integration.domain.CallVisitorRequest;
import ru.suo.integration.domain.CallVisitorResponse;
import ru.suo.integration.domain.QueueItemDto;

import java.util.List;

/**
 * Абстракция клиента к VisitManager.
 */
public interface VisitManagerClient {

    List<QueueItemDto> getQueues(String targetVisitManagerId, String branchId);

    CallVisitorResponse callVisitor(String targetVisitManagerId, String visitorId, CallVisitorRequest request);
}
