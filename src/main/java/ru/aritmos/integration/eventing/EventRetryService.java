package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.time.Instant;

/**
 * Retry/DLQ состояние для этапа 6 (in-memory).
 */
@Singleton
public class EventRetryService {

    private final Map<String, IntegrationEvent> dlq = new ConcurrentHashMap<>();

    public void toDlq(IntegrationEvent event) {
        dlq.put(event.eventId(), event);
    }

    public void toDlqAll(List<IntegrationEvent> events) {
        if (events == null) {
            return;
        }
        for (IntegrationEvent event : events) {
            if (event != null && event.eventId() != null) {
                dlq.put(event.eventId(), event);
            }
        }
    }

    public IntegrationEvent getById(String eventId) {
        return dlq.get(eventId);
    }

    public void remove(String eventId) {
        dlq.remove(eventId);
    }

    public boolean contains(String eventId) {
        return dlq.containsKey(eventId);
    }

    public void clear() {
        dlq.clear();
    }

    public int size() {
        return dlq.size();
    }

    public List<IntegrationEvent> dlqSnapshot() {
        return List.copyOf(dlq.values());
    }

    public Set<String> ids() {
        return Set.copyOf(dlq.keySet());
    }

    public int pruneByRetentionSeconds(long retentionSeconds, Instant now) {
        int removed = 0;
        Instant threshold = now.minusSeconds(retentionSeconds);
        for (IntegrationEvent event : dlq.values()) {
            if (event.occurredAt() != null && event.occurredAt().isBefore(threshold)) {
                if (dlq.remove(event.eventId()) != null) {
                    removed++;
                }
            }
        }
        return removed;
    }

    public int trimToMaxSize(int maxSize) {
        if (maxSize < 0) {
            return 0;
        }
        int toRemove = Math.max(0, dlq.size() - maxSize);
        if (toRemove == 0) {
            return 0;
        }
        List<IntegrationEvent> oldest = dlq.values().stream()
                .sorted(Comparator.comparing(IntegrationEvent::occurredAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .limit(toRemove)
                .toList();
        oldest.forEach(e -> dlq.remove(e.eventId()));
        return oldest.size();
    }
}
