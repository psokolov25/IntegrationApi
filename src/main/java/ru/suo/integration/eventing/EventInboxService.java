package ru.suo.integration.eventing;

import jakarta.inject.Singleton;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inbox/idempotency слой для защиты от повторной обработки.
 */
@Singleton
public class EventInboxService {

    private final Set<String> processedIds = ConcurrentHashMap.newKeySet();

    public boolean markIfFirst(String eventId) {
        return processedIds.add(eventId);
    }

    public boolean contains(String eventId) {
        return processedIds.contains(eventId);
    }

    public int size() {
        return processedIds.size();
    }

    public void clear() {
        processedIds.clear();
    }

    public int removeAll(Set<String> eventIds) {
        int removed = 0;
        for (String eventId : eventIds) {
            if (processedIds.remove(eventId)) {
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
            if (eventId != null && processedIds.add(eventId)) {
                added++;
            }
        }
        return added;
    }
}
