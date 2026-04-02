package ru.aritmos.integration.eventing;

import io.micronaut.core.annotation.Introspected;

import java.time.Instant;
import java.util.Map;

/**
 * Унифицированное входящее интеграционное событие.
 */
@Introspected
public record IntegrationEvent(String eventId, String eventType, String source, Instant occurredAt, Map<String, Object> payload) {
}
