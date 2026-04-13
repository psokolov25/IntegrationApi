package ru.aritmos.integration.service;

import jakarta.inject.Singleton;
import ru.aritmos.integration.audit.AuditService;
import ru.aritmos.integration.client.VisitManagerClient;
import ru.aritmos.integration.domain.AggregatedQueuesResponse;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.domain.BranchStateUpdateRequest;
import ru.aritmos.integration.domain.CallVisitorRequest;
import ru.aritmos.integration.domain.CallVisitorResponse;
import ru.aritmos.integration.domain.QueueItemDto;
import ru.aritmos.integration.domain.QueueListResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Оркестрация запросов к VisitManager в рамках gateway/federation.
 */
@Singleton
public class GatewayService {

    private final RoutingService routingService;
    private final VisitManagerClient visitManagerClient;
    private final QueueCache queueCache;
    private final BranchStateCache branchStateCache;
    private final AuditService auditService;
    private final VisitManagerMetricsService metricsService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(8);

    public GatewayService(RoutingService routingService,
                          VisitManagerClient visitManagerClient,
                          QueueCache queueCache,
                          BranchStateCache branchStateCache,
                          AuditService auditService,
                          VisitManagerMetricsService metricsService) {
        this.routingService = routingService;
        this.visitManagerClient = visitManagerClient;
        this.queueCache = queueCache;
        this.branchStateCache = branchStateCache;
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
        List<String> uniqueBranchIds = uniqueBranchIds(branchIds);
        List<CompletableFuture<QueueListResponse>> futures = uniqueBranchIds.stream()
                .map(branchId -> CompletableFuture.supplyAsync(() -> getQueues(subject, branchId, ""), executorService))
                .toList();

        List<QueueListResponse> successful = new ArrayList<>();
        List<AggregatedQueuesResponse.BranchError> failed = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            String branchId = uniqueBranchIds.get(i);
            String target = routingService.resolveTarget(branchId, "");
            try {
                successful.add(futures.get(i).get(routingService.aggregateRequestTimeoutMillis(), TimeUnit.MILLISECONDS));
            } catch (TimeoutException timeoutEx) {
                futures.get(i).cancel(true);
                failed.add(new AggregatedQueuesResponse.BranchError(
                        branchId,
                        target,
                        "Превышен timeout агрегации: " + routingService.aggregateRequestTimeoutMillis() + " мс"
                ));
                auditService.auditDenied("queue-view-aggregate-timeout", subject);
            } catch (Exception ex) {
                failed.add(new AggregatedQueuesResponse.BranchError(branchId, target, rootMessage(ex)));
                auditService.auditDenied("queue-view-aggregate", subject);
            }
        }

        return new AggregatedQueuesResponse(successful, failed, !failed.isEmpty());
    }


    private List<String> uniqueBranchIds(List<String> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return List.of();
        }
        return branchIds.stream()
                .map(branchId -> branchId == null ? null : branchId.trim())
                .filter(branchId -> branchId != null && !branchId.isBlank())
                .distinct()
                .toList();
    }


    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    public CallVisitorResponse callVisitor(String subject, String visitorId, CallVisitorRequest request, String explicitTarget) {
        String target = routingService.resolveTarget(request.branchId(), explicitTarget);
        queueCache.invalidate(target + ":" + request.branchId());
        CallVisitorResponse response = visitManagerClient.callVisitor(target, visitorId, request);
        metricsService.recordSuccess(target);
        auditService.auditSuccess("queue-call", subject, target);
        return response;
    }

    public BranchStateDto getBranchState(String subject, String branchId, String explicitTarget) {
        String target = routingService.resolveTarget(branchId, explicitTarget);
        BranchStateDto cached = branchStateCache.get(target, branchId);
        if (cached != null) {
            auditService.auditSuccess("branch-state-view-cache", subject, cached.sourceVisitManagerId());
            return cached;
        }
        return refreshBranchState(subject, branchId, target);
    }

    public BranchStateDto refreshBranchState(String subject, String branchId, String explicitTarget) {
        String target = routingService.resolveTarget(branchId, explicitTarget);
        BranchStateDto loaded = visitManagerClient.getBranchState(target, branchId);
        branchStateCache.put(loaded);
        metricsService.recordSuccess(target);
        auditService.auditSuccess("branch-state-refresh", subject, target);
        return loaded;
    }

    public BranchStateDto updateBranchState(String subject,
                                            String branchId,
                                            BranchStateUpdateRequest request,
                                            String explicitTarget) {
        String target = routingService.resolveTarget(branchId, explicitTarget);
        BranchStateDto updated = visitManagerClient.updateBranchState(target, branchId, request);
        branchStateCache.put(updated);
        metricsService.recordSuccess(target);
        auditService.auditSuccess("branch-state-manage", subject, target);
        return updated;
    }

    public boolean applyEventBranchState(BranchStateDto state) {
        return branchStateCache.putIfNewer(state);
    }

    public List<BranchStateDto> branchStateSnapshot() {
        return branchStateCache.snapshot();
    }
}
