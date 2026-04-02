package ru.suo.integration.eventing;

import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.time.Instant;

/**
 * In-memory store обработанных событий для replay.
 */
@Singleton
public class EventStoreService {

    private final Map<String, IntegrationEvent> processed = new ConcurrentHashMap<>();

    public void saveProcessed(IntegrationEvent event) {
        processed.put(event.eventId(), event);
    }

    public void saveAll(Map<String, IntegrationEvent> events) {
        if (events == null) {
            return;
        }
        processed.putAll(events);
    }

    public IntegrationEvent getById(String eventId) {
        return processed.get(eventId);
    }

    public Map<String, IntegrationEvent> snapshot() {
        return Map.copyOf(processed);
    }

    public int size() {
        return processed.size();
    }

    public void clear() {
        processed.clear();
    }

    public int pruneByRetentionSeconds(long retentionSeconds, Instant now) {
        int removed = 0;
        Instant threshold = now.minusSeconds(retentionSeconds);
        for (IntegrationEvent event : processed.values()) {
            if (event.occurredAt() != null && event.occurredAt().isBefore(threshold)) {
                if (processed.remove(event.eventId()) != null) {
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
        int toRemove = Math.max(0, processed.size() - maxSize);
        if (toRemove == 0) {
            return 0;
        }
        var oldest = processed.values().stream()
                .sorted(Comparator.comparing(IntegrationEvent::occurredAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .limit(toRemove)
                .toList();
        oldest.forEach(e -> processed.remove(e.eventId()));
        return oldest.size();
    }
}
