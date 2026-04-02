package ru.aritmos.integration.eventing.visitmanager;

import io.micronaut.core.annotation.Introspected;

import java.time.Instant;

/**
 * Каноническая модель события изменения состояния отделения из VisitManager DataBus.
 */
@Introspected
public record VisitManagerBranchStateEventPayload(
        String sourceVisitManagerId,
        String branchId,
        String status,
        String activeWindow,
        int queueSize,
        Instant updatedAt,
        String updatedBy
) {
}
