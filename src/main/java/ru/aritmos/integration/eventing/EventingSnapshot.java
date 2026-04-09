package ru.aritmos.integration.eventing;

import io.micronaut.core.annotation.Introspected;

import java.util.List;
import java.util.Map;

/**
 * Экспортный снимок in-memory состояния eventing.
 */
@Introspected
public record EventingSnapshot(
        Map<String, IntegrationEvent> processed,
        List<IntegrationEvent> dlq,
        Map<String, EventOutboxMessage> outbox,
        EventingStats stats
) {
}
