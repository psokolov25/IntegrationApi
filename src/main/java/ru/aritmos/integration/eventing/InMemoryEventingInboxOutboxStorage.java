package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory хранилище inbox/outbox (без персистентности между рестартами).
 */
@Singleton
public class InMemoryEventingInboxOutboxStorage implements EventingInboxOutboxStorage {

    private final Map<String, EventInboxService.InboxEntry> inbox = new ConcurrentHashMap<>();
    private final Map<String, EventOutboxMessage> outbox = new ConcurrentHashMap<>();
    private final Map<String, Object> runtimeSettings = new ConcurrentHashMap<>();

    @Override
    public Map<String, EventInboxService.InboxEntry> loadInbox() {
        return Map.copyOf(inbox);
    }

    @Override
    public void saveInbox(Map<String, EventInboxService.InboxEntry> snapshot) {
        inbox.clear();
        if (snapshot != null) {
            inbox.putAll(snapshot);
        }
    }

    @Override
    public Map<String, EventOutboxMessage> loadOutbox() {
        return Map.copyOf(outbox);
    }

    @Override
    public void saveOutbox(Map<String, EventOutboxMessage> snapshot) {
        outbox.clear();
        if (snapshot != null) {
            outbox.putAll(snapshot);
        }
    }

    @Override
    public Map<String, Object> loadRuntimeSettings() {
        return Map.copyOf(runtimeSettings);
    }

    @Override
    public void saveRuntimeSettings(Map<String, Object> snapshot) {
        runtimeSettings.clear();
        if (snapshot != null) {
            runtimeSettings.putAll(snapshot);
        }
    }
}
