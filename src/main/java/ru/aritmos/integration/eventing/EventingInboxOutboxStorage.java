package ru.aritmos.integration.eventing;

import java.util.Map;

/**
 * Контракт хранилища inbox/outbox для eventing-пайплайна.
 */
public interface EventingInboxOutboxStorage {

    Map<String, EventInboxService.InboxEntry> loadInbox();

    void saveInbox(Map<String, EventInboxService.InboxEntry> snapshot);

    Map<String, EventOutboxMessage> loadOutbox();

    void saveOutbox(Map<String, EventOutboxMessage> snapshot);

    Map<String, Object> loadRuntimeSettings();

    void saveRuntimeSettings(Map<String, Object> snapshot);
}
