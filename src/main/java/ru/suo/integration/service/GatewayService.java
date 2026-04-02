package ru.suo.integration.service;

import jakarta.inject.Singleton;
import ru.suo.integration.audit.AuditService;
import ru.suo.integration.client.VisitManagerClient;
import ru.suo.integration.domain.AggregatedQueuesResponse;
import ru.suo.integration.domain.CallVisitorRequest;
import ru.suo.integration.domain.CallVisitorResponse;
import ru.suo.integration.domain.QueueItemDto;
import ru.suo.integration.domain.QueueListResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Оркестрация запросов к VisitManager в рамках gateway/federation.
 */
@Singleton
public class GatewayService {

    private final RoutingService routingService;
    private final VisitManagerClient visitManagerClient;
    private final QueueCache queueCache;
    private final AuditService auditService;
    private final VisitManagerMetricsService metricsService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(8);

    public GatewayService(RoutingService routingService,
                          VisitManagerClient visitManagerClient,
                          QueueCache queueCache,
                          AuditService auditService,
                          VisitManagerMetricsService metricsService) {
        this.routingService = routingService;
        this.visitManagerClient = visitManagerClient;
        this.queueCache = queueCache;
        this.auditService = auditService;
        this.metricsService = metricsService;
    }

    public QueueListResponse getQueues(String subject, String branchId, String explicitTarget) {
        String target = routingService.resolveTarget(branchId, explicitTarget);
        return getQueuesWithFallback(subject, branchId, target);
    }

    private QueueListResponse getQueuesWithFallback(String subject, String branchId, String target) {
        String cacheKey = target + ":" + branchId;
        List<QueueItemDto> cached = queueCache.get(cacheKey);
        if (cached != null) {
            metricsService.recordSuccess(target);
            auditService.auditSuccess("queue-view", subject, target);
            return new QueueListResponse(branchId, target, cached, true);
        }

        try {
            List<QueueItemDto> loaded = visitManagerClient.getQueues(target, branchId);
            queueCache.put(cacheKey, loaded);
            metricsService.recordSuccess(target);
            auditService.auditSuccess("queue-view", subject, target);
            return new QueueListResponse(branchId, target, loaded, false);
        } catch (Exception primaryError) {
            metricsService.recordError(target);
            String fallbackTarget = routingService.resolveFallbackTarget(branchId, target);
            if (fallbackTarget == null) {
                throw primaryError;
            }
            List<QueueItemDto> loaded = visitManagerClient.getQueues(fallbackTarget, branchId);
            queueCache.put(fallbackTarget + ":" + branchId, loaded);
            metricsService.recordSuccess(fallbackTarget);
            auditService.auditSuccess("queue-view-fallback", subject, fallbackTarget);
            return new QueueListResponse(branchId, fallbackTarget, loaded, false);
        }
    }

    public AggregatedQueuesResponse getAggregatedQueues(String subject, List<String> branchIds) {
        List<CompletableFuture<QueueListResponse>> futures = branchIds.stream()
                .map(branchId -> CompletableFuture.supplyAsync(() -> getQueues(subject, branchId, ""), executorService))
                .toList();

        List<QueueListResponse> successful = new ArrayList<>();
        List<AggregatedQueuesResponse.BranchError> failed = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            String branchId = branchIds.get(i);
            String target = routingService.resolveTarget(branchId, "");
            try {
                successful.add(futures.get(i).join());
            } catch (Exception ex) {
                failed.add(new AggregatedQueuesResponse.BranchError(branchId, target, ex.getMessage()));
                auditService.auditDenied("queue-view-aggregate", subject);
            }
        }

        return new AggregatedQueuesResponse(successful, failed, !failed.isEmpty());
    }

    public CallVisitorResponse callVisitor(String subject, String visitorId, CallVisitorRequest request, String explicitTarget) {
        String target = routingService.resolveTarget(request.branchId(), explicitTarget);
        queueCache.invalidate(target + ":" + request.branchId());
        CallVisitorResponse response = visitManagerClient.callVisitor(target, visitorId, request);
        metricsService.recordSuccess(target);
        auditService.auditSuccess("queue-call", subject, target);
        return response;
    }
}
