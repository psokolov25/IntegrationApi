package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event pipeline: ingestion -> validation -> idempotency -> dispatch -> retry/DLQ.
 */
@Singleton
public class EventDispatcherService implements EventOutboxFlusher {

    private final IntegrationGatewayConfiguration configuration;
    private final EventInboxService inboxService;
    private final EventRetryService retryService;
    private final EventStoreService eventStoreService;
    private final EventOutboxService outboxService;
    private final EventTransportAdapter transportAdapter;
    private final List<EventHandler> handlers;
    private final EventingStatePersistenceService persistenceService;
    private final AtomicLong processedCount = new AtomicLong();
    private final AtomicLong duplicateCount = new AtomicLong();
    private final AtomicLong dlqCount = new AtomicLong();
    private final AtomicLong replayCount = new AtomicLong();
    private final AtomicLong replaySequence = new AtomicLong();

    public EventDispatcherService(IntegrationGatewayConfiguration configuration,
                                  EventInboxService inboxService,
                                  EventRetryService retryService,
                                  EventStoreService eventStoreService,
                                  EventOutboxService outboxService,
                                  EventTransportAdapter transportAdapter,
                                  List<EventHandler> handlers,
                                  EventingStatePersistenceService persistenceService) {
        this.configuration = configuration;
        this.inboxService = inboxService;
        this.retryService = retryService;
        this.eventStoreService = eventStoreService;
        this.outboxService = outboxService;
        this.transportAdapter = transportAdapter;
        this.handlers = handlers;
        this.persistenceService = persistenceService;
        restoreStateIfPresent();
    }

    public EventDispatcherService(IntegrationGatewayConfiguration configuration,
                                  EventInboxService inboxService,
                                  EventRetryService retryService,
                                  EventStoreService eventStoreService,
                                  EventOutboxService outboxService,
                                  EventTransportAdapter transportAdapter,
                                  List<EventHandler> handlers) {
        this(configuration, inboxService, retryService, eventStoreService, outboxService, transportAdapter, handlers, null);
    }

    public EventDispatcherService(IntegrationGatewayConfiguration configuration,
                                  EventInboxService inboxService,
                                  EventRetryService retryService,
                                  EventStoreService eventStoreService,
                                  EventTransportAdapter transportAdapter,
                                  List<EventHandler> handlers) {
        this(configuration, inboxService, retryService, eventStoreService, new EventOutboxService(), transportAdapter, handlers, null);
    }

    public EventProcessingResult process(IntegrationEvent event) {
        if (!configuration.getEventing().isEnabled()) {
            throw new IllegalStateException("Eventing отключен");
        }
        validate(event);
        try {
            EventInboxService.InboxState inboxState = inboxService.beginProcessing(event.eventId());
            if (inboxState == EventInboxService.InboxState.DUPLICATE
                    || inboxState == EventInboxService.InboxState.IN_PROGRESS) {
                duplicateCount.incrementAndGet();
                flushOutboxByEventId(event.eventId(), 1);
                return new EventProcessingResult(event.eventId(), "DUPLICATE", "Событие уже обработано", 0);
            }
            if (inboxState == EventInboxService.InboxState.INVALID) {
                throw new IllegalArgumentException("eventId обязателен");
            }

            int attempts = 0;
            while (attempts <= configuration.getEventing().getMaxRetries()) {
                attempts++;
                try {
                    EventHandler handler = handlers.stream()
                            .filter(item -> item.supports(event.eventType()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Не найден handler для eventType=" + event.eventType()));
                    handler.handle(event);
                    eventStoreService.saveProcessed(event);
                    outboxService.stage(event);
                    boolean sent = flushOutboxByEventId(event.eventId(), configuration.getEventing().getMaxRetries() + 1);
                    inboxService.markProcessed(event.eventId());
                    if (!sent) {
                        return new EventProcessingResult(
                                event.eventId(),
                                "OUTBOX_PENDING",
                                "Событие обработано, отправка во внешний транспорт отложена (outbox)",
                                attempts
                        );
                    }
                    processedCount.incrementAndGet();
                    return new EventProcessingResult(event.eventId(), "PROCESSED", "OK", attempts);
                } catch (Exception ex) {
                    if (attempts > configuration.getEventing().getMaxRetries()) {
                        retryService.toDlq(event);
                        inboxService.markFailed(event.eventId(), ex.getMessage());
                        dlqCount.incrementAndGet();
                        return new EventProcessingResult(event.eventId(), "DLQ", ex.getMessage(), attempts);
                    }
                }
            }
            retryService.toDlq(event);
            inboxService.markFailed(event.eventId(), "Unknown failure");
            dlqCount.incrementAndGet();
            return new EventProcessingResult(event.eventId(), "DLQ", "Unknown failure", attempts);
        } finally {
            persistState();
        }
    }

    public EventProcessingResult replay(String eventId) {
        IntegrationEvent event = eventStoreService.getById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("Событие для replay не найдено: " + eventId);
        }
        replayCount.incrementAndGet();
        return process(withReplayId(event, "replay"));
    }

    public EventProcessingResult replayFromDlq(String eventId) {
        IntegrationEvent event = retryService.getById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("Событие в DLQ не найдено: " + eventId);
        }
        replayCount.incrementAndGet();
        EventProcessingResult result = process(withReplayId(event, "dlq-replay"));
        if ("PROCESSED".equals(result.status())) {
            retryService.remove(eventId);
        }
        return result;
    }

    public EventingStats stats() {
        return new EventingStats(
                processedCount.get(),
                duplicateCount.get(),
                dlqCount.get(),
                replayCount.get(),
                inboxService.size(),
                inboxService.processingSize(),
                eventStoreService.size(),
                retryService.size(),
                outboxService.size(),
                outboxService.pendingSize(),
                outboxService.failedSize(),
                outboxService.deadSize()
        );
    }

    public IntegrationEvent processedEvent(String eventId) {
        return eventStoreService.getById(eventId);
    }

    public Map<String, IntegrationEvent> processedEvents() {
        return eventStoreService.snapshot();
    }

    public List<EventProcessingResult> replayAllFromDlq(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit должен быть > 0");
        }
        List<EventProcessingResult> results = new ArrayList<>();
        int counter = 0;
        for (String eventId : retryService.ids()) {
            if (counter >= limit) {
                break;
            }
            results.add(replayFromDlq(eventId));
            counter++;
        }
        return results;
    }

    public List<EventInboxService.InboxEntry> inboxSnapshot(int limit, String statusFilter) {
        return inboxService.snapshot(limit, statusFilter);
    }

    public int clearInboxByStatus(String statusFilter) {
        int removed = inboxService.removeByStatus(statusFilter);
        if (removed > 0) {
            persistState();
        }
        return removed;
    }

    public void resetStats() {
        processedCount.set(0);
        duplicateCount.set(0);
        dlqCount.set(0);
        replayCount.set(0);
    }

    public void clearProcessedStore() {
        eventStoreService.clear();
        inboxService.clear();
        outboxService.clear();
        persistState();
    }

    public EventingHealth health() {
        EventingStats stats = stats();
        List<String> reasons = new ArrayList<>();
        if (stats.dlqSize() >= configuration.getEventing().getDlqWarnThreshold()) {
            reasons.add("DLQ size достиг порога: " + stats.dlqSize());
        }
        if (stats.duplicateCount() >= configuration.getEventing().getDuplicateWarnThreshold()) {
            reasons.add("Duplicate count достиг порога: " + stats.duplicateCount());
        }
        String status = reasons.isEmpty() ? "UP" : "DEGRADED";
        return new EventingHealth(status, reasons, stats);
    }

    public EventingMaintenanceReport runMaintenance() {
        Instant now = Instant.now();
        int removedFromDlq = retryService.pruneByRetentionSeconds(configuration.getEventing().getRetentionSeconds(), now);
        removedFromDlq += retryService.trimToMaxSize(configuration.getEventing().getMaxDlqEvents());

        int removedFromProcessed = eventStoreService.pruneByRetentionSeconds(configuration.getEventing().getRetentionSeconds(), now);
        removedFromProcessed += eventStoreService.trimToMaxSize(configuration.getEventing().getMaxProcessedEvents());

        int removedFromInbox = 0;
        if (removedFromProcessed > 0) {
            removedFromInbox = inboxService.size();
            inboxService.clear();
        }

        EventingMaintenanceReport report = new EventingMaintenanceReport(removedFromDlq, removedFromProcessed, removedFromInbox, stats());
        persistState();
        return report;
    }

    public EventingMaintenanceReport previewMaintenance() {
        Instant now = Instant.now();
        int dlqWillRemoveByRetention = (int) retryService.dlqSnapshot().stream()
                .filter(e -> e.occurredAt() != null && e.occurredAt().isBefore(now.minusSeconds(configuration.getEventing().getRetentionSeconds())))
                .count();
        int dlqWillRemoveBySize = Math.max(0, retryService.size() - configuration.getEventing().getMaxDlqEvents());

        int processedWillRemoveByRetention = (int) eventStoreService.snapshot().values().stream()
                .filter(e -> e.occurredAt() != null && e.occurredAt().isBefore(now.minusSeconds(configuration.getEventing().getRetentionSeconds())))
                .count();
        int processedWillRemoveBySize = Math.max(0, eventStoreService.size() - configuration.getEventing().getMaxProcessedEvents());

        int removedFromDlq = dlqWillRemoveByRetention + dlqWillRemoveBySize;
        int removedFromProcessed = processedWillRemoveByRetention + processedWillRemoveBySize;
        int removedFromInbox = removedFromProcessed > 0 ? inboxService.size() : 0;
        return new EventingMaintenanceReport(removedFromDlq, removedFromProcessed, removedFromInbox, stats());
    }

    public EventingSnapshot exportSnapshot() {
        return new EventingSnapshot(eventStoreService.snapshot(), retryService.dlqSnapshot(), outboxService.snapshot(), stats());
    }

    public EventingImportResult importSnapshot(EventingSnapshot snapshot, boolean clearBeforeImport) {
        EventingSnapshotValidation validation = validateSnapshot(snapshot, true);
        if (!validation.valid()) {
            throw new IllegalArgumentException("snapshot невалиден: " + summarizeViolations(validation));
        }
        if (clearBeforeImport) {
            retryService.clear();
            eventStoreService.clear();
            inboxService.clear();
            outboxService.clear();
        }
        Map<String, IntegrationEvent> processed = snapshot.processed() == null ? Map.of() : snapshot.processed();
        List<IntegrationEvent> dlq = snapshot.dlq() == null ? List.of() : snapshot.dlq();
        Map<String, EventOutboxMessage> outbox = snapshot.outbox() == null ? Map.of() : snapshot.outbox();
        eventStoreService.saveAll(processed);
        retryService.toDlqAll(dlq);
        outboxService.saveAll(outbox);
        int marked = inboxService.markAll(processed.keySet());
        EventingImportResult result = new EventingImportResult(
                processed.size(),
                dlq.size(),
                marked,
                stats()
        );
        persistState();
        return result;
    }

    public EventingImportResult previewImport(EventingSnapshot snapshot) {
        return previewImport(snapshot, false, true);
    }

    public EventingImportResult previewImport(EventingSnapshot snapshot, boolean clearBeforeImport) {
        return previewImport(snapshot, clearBeforeImport, true);
    }

    public EventingImportResult previewImport(EventingSnapshot snapshot, boolean clearBeforeImport, boolean strictPolicies) {
        EventingSnapshotValidation validation = validateSnapshot(snapshot, strictPolicies);
        if (!validation.valid()) {
            throw new IllegalArgumentException("snapshot невалиден: " + summarizeViolations(validation));
        }
        Map<String, IntegrationEvent> processed = snapshot.processed() == null ? Map.of() : snapshot.processed();
        List<IntegrationEvent> dlq = snapshot.dlq() == null ? List.of() : snapshot.dlq();
        EventingStats current = stats();
        int projectedProcessedStore = clearBeforeImport ? processed.size() : current.processedStoreSize() + processed.size();
        int projectedDlqSize = clearBeforeImport ? dlq.size() : current.dlqSize() + dlq.size();
        int projectedInboxSize = clearBeforeImport ? processed.size() : current.inboxSize() + processed.size();
        int projectedOutboxSize = clearBeforeImport ? 0 : current.outboxSize();
        return new EventingImportResult(
                processed.size(),
                dlq.size(),
                processed.size(),
                new EventingStats(
                        current.processedCount(),
                        current.duplicateCount(),
                        current.dlqCount(),
                        current.replayCount(),
                        projectedInboxSize,
                        current.inboxInProgressSize(),
                        projectedProcessedStore,
                        projectedDlqSize,
                        projectedOutboxSize,
                        current.outboxPendingSize(),
                        current.outboxFailedSize(),
                        current.outboxDeadSize()
                )
        );
    }

    public EventingImportAnalysis analyzeImport(EventingSnapshot snapshot, boolean clearBeforeImport, boolean strictPolicies) {
        EventingSnapshotValidation validation = validateSnapshot(snapshot, strictPolicies);
        int processed = snapshot == null || snapshot.processed() == null ? 0 : snapshot.processed().size();
        int dlq = snapshot == null || snapshot.dlq() == null ? 0 : snapshot.dlq().size();
        int total = processed + dlq;
        EventingStats current = stats();
        int projectedProcessedStore = clearBeforeImport ? processed : current.processedStoreSize() + processed;
        int projectedDlqSize = clearBeforeImport ? dlq : current.dlqSize() + dlq;
        int projectedInboxSize = clearBeforeImport ? processed : current.inboxSize() + processed;
        int projectedOutboxSize = clearBeforeImport ? 0 : current.outboxSize();
        int usagePercent = configuration.getEventing().getSnapshotImportMaxEvents() <= 0
                ? 0
                : (int) Math.min(100, Math.round((double) total * 100 / configuration.getEventing().getSnapshotImportMaxEvents()));
        return new EventingImportAnalysis(
                validation.valid(),
                strictPolicies,
                clearBeforeImport,
                processed,
                dlq,
                total,
                usagePercent,
                projectedProcessedStore > configuration.getEventing().getMaxProcessedEvents(),
                projectedDlqSize > configuration.getEventing().getMaxDlqEvents(),
                new EventingStats(
                        current.processedCount(),
                        current.duplicateCount(),
                        current.dlqCount(),
                        current.replayCount(),
                        projectedInboxSize,
                        current.inboxInProgressSize(),
                        projectedProcessedStore,
                        projectedDlqSize,
                        projectedOutboxSize,
                        current.outboxPendingSize(),
                        current.outboxFailedSize(),
                        current.outboxDeadSize()
                ),
                validation
        );
    }

    public EventingCapabilities capabilities() {
        return new EventingCapabilities(
                true,
                true,
                true,
                true,
                true,
                true,
                true
        );
    }

    public EventingLimits limits() {
        return new EventingLimits(
                configuration.getEventing().getSnapshotImportMaxEvents(),
                configuration.getEventing().isSnapshotImportRequireMatchingProcessedKeys(),
                configuration.getEventing().isSnapshotImportRejectCrossListDuplicates(),
                configuration.getEventing().getMaxDlqEvents(),
                configuration.getEventing().getMaxProcessedEvents(),
                configuration.getEventing().getRetentionSeconds()
        );
    }

    public EventingSnapshotValidation validateSnapshot(EventingSnapshot snapshot) {
        return validateSnapshot(snapshot, true);
    }

    public EventingSnapshotValidation validateSnapshot(EventingSnapshot snapshot, boolean strictPolicies) {
        List<EventingSnapshotViolation> violations = new ArrayList<>();
        if (snapshot == null) {
            return new EventingSnapshotValidation(false, 0, 0, 0, List.of(
                    violation("SNAPSHOT_REQUIRED", "snapshot", "snapshot обязателен")
            ));
        }
        int processedSize = snapshot.processed() == null ? 0 : snapshot.processed().size();
        int dlqSize = snapshot.dlq() == null ? 0 : snapshot.dlq().size();
        int total = processedSize + dlqSize;
        if (total > configuration.getEventing().getSnapshotImportMaxEvents()) {
            violations.add(violation("SNAPSHOT_LIMIT_EXCEEDED", "snapshot", "snapshot превышает лимит import: " + total));
        }
        if (snapshot.processed() != null) {
            for (Map.Entry<String, IntegrationEvent> entry : snapshot.processed().entrySet()) {
                String key = entry.getKey();
                IntegrationEvent event = entry.getValue();
                if (event == null) {
                    violations.add(violation("PROCESSED_NULL_EVENT", "processed[" + key + "]", "processed[" + key + "] содержит null"));
                    continue;
                }
                validateImportedEvent(violations, event, "processed[" + key + "]");
                if (strictPolicies
                        && configuration.getEventing().isSnapshotImportRequireMatchingProcessedKeys()
                        && event.eventId() != null
                        && !event.eventId().equals(key)) {
                    violations.add(violation(
                            "PROCESSED_KEY_EVENT_ID_MISMATCH",
                            "processed[" + key + "]",
                            "processed key/eventId mismatch: " + key + " != " + event.eventId()
                    ));
                }
            }
        }
        Set<String> dlqIds = new HashSet<>();
        if (snapshot.dlq() != null) {
            int index = 0;
            for (IntegrationEvent event : snapshot.dlq()) {
                if (event == null) {
                    violations.add(violation("DLQ_NULL_EVENT", "dlq[" + index + "]", "dlq[" + index + "] содержит null"));
                    index++;
                    continue;
                }
                validateImportedEvent(violations, event, "dlq[" + index + "]");
                if (event.eventId() != null && !dlqIds.add(event.eventId())) {
                    violations.add(violation("DLQ_DUPLICATE_EVENT_ID", "dlq[" + index + "]", "dlq содержит duplicate eventId: " + event.eventId()));
                }
                index++;
            }
        }
        if (strictPolicies
                && configuration.getEventing().isSnapshotImportRejectCrossListDuplicates()
                && snapshot.processed() != null) {
            for (String processedId : snapshot.processed().keySet()) {
                if (dlqIds.contains(processedId)) {
                    violations.add(violation(
                            "CROSS_LIST_DUPLICATE_EVENT_ID",
                            "processed[" + processedId + "]",
                            "eventId присутствует и в processed, и в dlq: " + processedId
                    ));
                }
            }
        }
        return new EventingSnapshotValidation(violations.isEmpty(), processedSize, dlqSize, total, List.copyOf(violations));
    }

    private void validateImportedEvent(List<EventingSnapshotViolation> violations, IntegrationEvent event, String location) {
        try {
            validate(event);
        } catch (IllegalArgumentException ex) {
            violations.add(violation("INVALID_EVENT", location, location + ": " + ex.getMessage()));
        }
    }

    private EventingSnapshotViolation violation(String code, String path, String message) {
        return new EventingSnapshotViolation(code, path, message);
    }

    private String summarizeViolations(EventingSnapshotValidation validation) {
        return validation.violations().stream()
                .map(item -> item.code() + "@" + item.path() + ": " + item.message())
                .reduce((a, b) -> a + "; " + b)
                .orElse("unknown violation");
    }

    private void validate(IntegrationEvent event) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("eventId обязателен");
        }
        if (event.eventType() == null || event.eventType().isBlank()) {
            throw new IllegalArgumentException("eventType обязателен");
        }
        if (event.source() == null || event.source().isBlank()) {
            throw new IllegalArgumentException("source обязателен");
        }
        if (event.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt обязателен");
        }
        Instant maxAllowed = Instant.now().plusSeconds(configuration.getEventing().getMaxFutureSkewSeconds());
        if (event.occurredAt().isAfter(maxAllowed)) {
            throw new IllegalArgumentException("occurredAt слишком далеко в будущем");
        }
        int payloadSize = event.payload() == null ? 0 : event.payload().size();
        if (payloadSize > configuration.getEventing().getMaxPayloadFields()) {
            throw new IllegalArgumentException("payload превышает допустимый размер");
        }
    }

    public List<EventOutboxMessage> outboxSnapshot(int limit) {
        return outboxSnapshot(limit, "", false);
    }

    public List<EventOutboxMessage> outboxSnapshot(int limit, String status, boolean includeSent) {
        return outboxService.snapshot(limit, status, includeSent);
    }

    public EventOutboxMessage outboxById(String eventId) {
        return outboxService.getById(eventId);
    }

    public List<EventProcessingResult> flushOutbox(int limit) {
        List<EventProcessingResult> results = new ArrayList<>();
        List<EventOutboxMessage> candidates = outboxService.pending(limit <= 0 ? Integer.MAX_VALUE : limit, Instant.now());
        for (EventOutboxMessage message : candidates) {
            boolean sent = flushOutboxByEventId(message.eventId(), configuration.getEventing().getMaxRetries() + 1);
            results.add(new EventProcessingResult(
                    message.eventId(),
                    sent ? "OUTBOX_SENT" : "OUTBOX_FAILED",
                    sent ? "Отправлено из outbox" : "Не удалось отправить из outbox",
                    message.attempts()
            ));
        }
        persistState();
        return results;
    }

    public EventProcessingResult retryOutboxByEventId(String eventId) {
        boolean sent = flushOutboxByEventId(eventId, configuration.getEventing().getMaxRetries() + 1);
        persistState();
        return new EventProcessingResult(
                eventId,
                sent ? "OUTBOX_SENT" : "OUTBOX_FAILED",
                sent ? "Отправлено из outbox" : "Не удалось отправить из outbox",
                1
        );
    }

    private boolean flushOutboxByEventId(String eventId, int maxAttempts) {
        EventOutboxMessage message = outboxService.getById(eventId);
        if (message == null || "SENT".equals(message.status()) || "DEAD".equals(message.status())) {
            return true;
        }
        for (int i = 0; i < Math.max(1, maxAttempts); i++) {
            try {
                outboxService.markAttempt(eventId);
                transportAdapter.publish(message.event());
                outboxService.markSent(eventId);
                return true;
            } catch (Exception ex) {
                outboxService.markFailed(
                        eventId,
                        ex.getMessage(),
                        configuration.getEventing().getOutboxBackoffSeconds(),
                        configuration.getEventing().getOutboxMaxAttempts()
                );
            }
        }
        return false;
    }

    public int recoverStaleInboxProcessing() {
        int recovered = inboxService.recoverStaleProcessing(configuration.getEventing().getInboxProcessingTimeoutSeconds());
        if (recovered > 0) {
            persistState();
        }
        return recovered;
    }

    private IntegrationEvent withReplayId(IntegrationEvent event, String suffix) {
        return new IntegrationEvent(
                event.eventId() + "-" + suffix + "-" + replaySequence.incrementAndGet(),
                event.eventType(),
                event.source(),
                event.occurredAt(),
                event.payload()
        );
    }

    private void restoreStateIfPresent() {
        if (persistenceService == null || !persistenceService.enabled()) {
            return;
        }
        persistenceService.load().ifPresent(snapshot -> {
            Map<String, IntegrationEvent> processed = snapshot.processed() == null ? Map.of() : snapshot.processed();
            List<IntegrationEvent> dlq = snapshot.dlq() == null ? List.of() : snapshot.dlq();
            Map<String, EventOutboxMessage> outbox = snapshot.outbox() == null ? Map.of() : snapshot.outbox();
            eventStoreService.saveAll(processed);
            retryService.toDlqAll(dlq);
            outboxService.saveAll(outbox);
            inboxService.markAll(processed.keySet());
            if (snapshot.stats() != null) {
                processedCount.set(snapshot.stats().processedCount());
                duplicateCount.set(snapshot.stats().duplicateCount());
                dlqCount.set(snapshot.stats().dlqCount());
                replayCount.set(snapshot.stats().replayCount());
            }
        });
    }

    private void persistState() {
        if (persistenceService == null || !persistenceService.enabled()) {
            return;
        }
        persistenceService.save(exportSnapshot());
    }
}
