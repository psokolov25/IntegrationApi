package ru.aritmos.integration.eventing;

import java.util.List;

/**
 * Контракт для сервисов, выполняющих retry/flush outbox-сообщений.
 */
public interface EventOutboxFlusher {

    List<EventProcessingResult> flushOutbox(int limit);
}
