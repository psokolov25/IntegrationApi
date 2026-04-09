package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inbox/idempotency слой для защиты от повторной обработки.
 */
@Singleton
public class EventInboxService {

    private final Map<String, InboxEntry> entries = new ConcurrentHashMap<>();

    public InboxState beginProcessing(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return InboxState.INVALID;
        }
        Instant now = Instant.now();
        InboxEntry updated = entries.compute(eventId, (id, current) -> {
            if (current == null) {
                return new InboxEntry(id, now, now, 1, "PROCESSING", null);
            }
            if ("PROCESSING".equals(current.status())) {
                return current;
            }
            if ("PROCESSED".equals(current.status())) {
                return current;
            }
            return new InboxEntry(id, current.firstSeenAt(), now, current.attempts() + 1, "PROCESSING", null);
        });
        if ("PROCESSING".equals(updated.status()) && updated.attempts() == 1) {
            return InboxState.FIRST;
        }
        if ("PROCESSING".equals(updated.status())) {
            return InboxState.IN_PROGRESS;
        }
        return InboxState.DUPLICATE;
    }

    public boolean markIfFirst(String eventId) {
        return beginProcessing(eventId) == InboxState.FIRST;
    }

    public void markProcessed(String eventId) {
        InboxEntry current = entries.get(eventId);
        if (current == null) {
            return;
        }
        entries.put(eventId, new InboxEntry(
                eventId,
                current.firstSeenAt(),
                Instant.now(),
                current.attempts(),
                "PROCESSED",
                null
        ));
    }

    public void markFailed(String eventId, String error) {
        InboxEntry current = entries.get(eventId);
        Instant now = Instant.now();
        if (current == null) {
            entries.put(eventId, new InboxEntry(eventId, now, now, 1, "FAILED", error));
            return;
        }
        entries.put(eventId, new InboxEntry(
                eventId,
                current.firstSeenAt(),
                now,
                current.attempts(),
                "FAILED",
                error
        ));
    }

    public boolean contains(String eventId) {
        return entries.containsKey(eventId);
    }

    public int size() {
        return entries.size();
    }

    public int processingSize() {
        return (int) entries.values().stream().filter(item -> "PROCESSING".equals(item.status())).count();
    }

    public int recoverStaleProcessing(long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            return 0;
        }
        Instant deadline = Instant.now().minusSeconds(timeoutSeconds);
        int recovered = 0;
        for (Map.Entry<String, InboxEntry> entry : entries.entrySet()) {
            InboxEntry current = entry.getValue();
            if ("PROCESSING".equals(current.status()) && current.updatedAt().isBefore(deadline)) {
                entries.put(entry.getKey(), new InboxEntry(
                        current.eventId(),
                        current.firstSeenAt(),
                        Instant.now(),
                        current.attempts(),
                        "FAILED",
                        "Stale processing timeout exceeded"
                ));
                recovered++;
            }
        }
        return recovered;
    }

    public void clear() {
        entries.clear();
    }

    public int removeAll(Set<String> eventIds) {
        int removed = 0;
        for (String eventId : eventIds) {
            if (entries.remove(eventId) != null) {
                removed++;
            }
        }
        return removed;
    }

    public int markAll(Set<String> eventIds) {
        if (eventIds == null) {
            return 0;
        }
        int added = 0;
        for (String eventId : eventIds) {
            if (eventId != null && entries.putIfAbsent(eventId, new InboxEntry(
                    eventId,
                    Instant.now(),
                    Instant.now(),
                    1,
                    "PROCESSED",
                    null
            )) == null) {
                added++;
            }
        }
        return added;
    }

    public List<InboxEntry> snapshot(int limit, String statusFilter) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit должен быть > 0");
        }
        String normalizedStatus = statusFilter == null ? "" : statusFilter.trim().toUpperCase();
        return entries.values().stream()
                .filter(entry -> normalizedStatus.isEmpty() || entry.status().equalsIgnoreCase(normalizedStatus))
                .sorted(Comparator.comparing(InboxEntry::updatedAt).reversed())
                .limit(limit)
                .toList();
    }

    public int removeByStatus(String statusFilter) {
        String normalized = statusFilter == null ? "" : statusFilter.trim().toUpperCase();
        if (normalized.isBlank()) {
            int removed = entries.size();
            entries.clear();
            return removed;
        }
        int removed = 0;
        for (Map.Entry<String, InboxEntry> entry : entries.entrySet()) {
            if (entry.getValue().status().equalsIgnoreCase(normalized) && entries.remove(entry.getKey()) != null) {
                removed++;
            }
        }
        return removed;
    }

    public enum InboxState {
        FIRST,
        DUPLICATE,
        IN_PROGRESS,
        INVALID
    }

    public record InboxEntry(
            String eventId,
            Instant firstSeenAt,
            Instant updatedAt,
            int attempts,
            String status,
            String lastError
    ) {
    }
}
