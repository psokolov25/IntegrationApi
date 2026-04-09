package ru.aritmos.integration.eventing;

import io.micronaut.core.annotation.Introspected;

import java.time.Instant;

/**
 * Сообщение outbox для надежной отправки во внешний транспорт.
 */
@Introspected
public record EventOutboxMessage(
        String eventId,
        IntegrationEvent event,
        String status,
        int attempts,
        String lastError,
        Instant nextRetryAt,
        Instant updatedAt
) {
}
