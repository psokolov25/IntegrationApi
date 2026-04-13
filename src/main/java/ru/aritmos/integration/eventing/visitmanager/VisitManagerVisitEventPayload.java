package ru.aritmos.integration.eventing.visitmanager;

import io.micronaut.core.annotation.Introspected;

import java.time.Instant;

/**
 * Каноническая минимальная модель события визита VisitManager для синхронизации branch-state.
 */
@Introspected
public record VisitManagerVisitEventPayload(
        String sourceVisitManagerId,
        String branchId,
        String visitEventType,
        Instant occurredAt,
        String canonicalEventId
) {
}
