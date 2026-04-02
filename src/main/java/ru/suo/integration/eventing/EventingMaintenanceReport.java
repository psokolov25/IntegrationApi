package ru.suo.integration.eventing;

import io.micronaut.core.annotation.Introspected;

/**
 * Результат maintenance-процедур eventing (prune/trim).
 */
@Introspected
public record EventingMaintenanceReport(
        int removedFromDlq,
        int removedFromProcessed,
        int removedFromInbox,
        EventingStats statsAfter
) {
}
