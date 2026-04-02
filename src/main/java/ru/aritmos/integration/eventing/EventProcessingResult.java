package ru.aritmos.integration.eventing;

import io.micronaut.core.annotation.Introspected;

/**
 * Результат обработки события.
 */
@Introspected
public record EventProcessingResult(String eventId, String status, String message, int attempts) {
}
