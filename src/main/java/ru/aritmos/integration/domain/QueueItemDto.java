package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;

/**
 * Унифицированная модель очереди для внешнего API.
 */
@Introspected
public record QueueItemDto(String queueId, String queueName, int waitingCount) {
}
