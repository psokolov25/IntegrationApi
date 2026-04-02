package ru.aritmos.integration.eventing;

import io.micronaut.core.annotation.Introspected;

/**
 * Результат импорта eventing snapshot.
 */
@Introspected
public record EventingImportResult(
        int importedProcessed,
        int importedDlq,
        int importedInboxIds,
        EventingStats statsAfter
) {
}
