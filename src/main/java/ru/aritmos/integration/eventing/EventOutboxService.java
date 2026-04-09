package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory outbox для надежной отправки интеграционных событий.
 */
@Singleton
public class EventOutboxService {

    private final Map<String, EventOutboxMessage> storage = new ConcurrentHashMap<>();

    public void stage(IntegrationEvent event) {
        storage.compute(event.eventId(), (id, current) -> {
            if (current == null) {
                Instant now = Instant.now();
                return new EventOutboxMessage(id, event, "PENDING", 0, null, now, now);
            }
            if ("SENT".equals(current.status())) {
                return current;
            }
            Instant now = Instant.now();
            return new EventOutboxMessage(id, event, "PENDING", current.attempts(), current.lastError(), now, now);
        });
    }

    public EventOutboxMessage markSent(String eventId) {
        return storage.computeIfPresent(eventId, (id, current) ->
                new EventOutboxMessage(id, current.event(), "SENT", current.attempts(), null, current.nextRetryAt(), Instant.now()));
    }

    public EventOutboxMessage markFailed(String eventId, String error, int backoffSeconds, int maxAttempts) {
        return storage.computeIfPresent(eventId, (id, current) -> {
            int attempts = current.attempts();
            Instant now = Instant.now();
            String status = attempts >= maxAttempts ? "DEAD" : "FAILED";
            Instant nextRetry = now.plusSeconds(Math.max(1, backoffSeconds));
            return new EventOutboxMessage(id, current.event(), status, attempts, error, nextRetry, now);
        });
    }

    public EventOutboxMessage markAttempt(String eventId) {
        return storage.computeIfPresent(eventId, (id, current) ->
                new EventOutboxMessage(id, current.event(), "PENDING", current.attempts() + 1, current.lastError(), current.nextRetryAt(), Instant.now()));
    }

    public EventOutboxMessage getById(String eventId) {
        return storage.get(eventId);
    }

    public List<EventOutboxMessage> pending(int limit) {
        return pending(limit, Instant.now());
    }

    public List<EventOutboxMessage> pending(int limit, Instant asOf) {
        return storage.values().stream()
                .filter(item -> !"SENT".equals(item.status()) && !"DEAD".equals(item.status()))
                .filter(item -> item.nextRetryAt() == null || !item.nextRetryAt().isAfter(asOf))
                .sorted(Comparator.comparing(EventOutboxMessage::updatedAt))
                .limit(Math.max(limit, 0))
                .toList();
    }

    public List<EventOutboxMessage> snapshot(int limit, String status, boolean includeSent) {
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        return storage.values().stream()
                .filter(item -> includeSent || !"SENT".equals(item.status()))
                .filter(item -> normalizedStatus.isBlank() || item.status().equalsIgnoreCase(normalizedStatus))
                .sorted(Comparator.comparing(EventOutboxMessage::updatedAt).reversed())
                .limit(limit <= 0 ? Integer.MAX_VALUE : limit)
                .toList();
    }

    public int size() {
        return storage.size();
    }

    public int pendingSize() {
        return (int) storage.values().stream().filter(item -> !"SENT".equals(item.status())).count();
    }

    public int failedSize() {
        return (int) storage.values().stream().filter(item -> "FAILED".equals(item.status())).count();
    }

    public int deadSize() {
        return (int) storage.values().stream().filter(item -> "DEAD".equals(item.status())).count();
    }

    public Map<String, EventOutboxMessage> snapshot() {
        return Map.copyOf(storage);
    }

    public void saveAll(Map<String, EventOutboxMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        storage.putAll(messages);
    }

    public void clear() {
        storage.clear();
    }
}
