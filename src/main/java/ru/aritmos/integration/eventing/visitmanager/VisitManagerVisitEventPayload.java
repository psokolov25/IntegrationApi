package ru.aritmos.integration.eventing.visitmanager;

import io.micronaut.core.annotation.Introspected;

/**
 * Каноническая минимальная модель события визита VisitManager для синхронизации branch-state.
 */
@Introspected
public record VisitManagerVisitEventPayload(
        String sourceVisitManagerId,
        String branchId,
        String visitEventType
) {
}
