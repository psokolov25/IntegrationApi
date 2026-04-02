package ru.aritmos.integration.eventing;

import io.micronaut.core.annotation.Introspected;

/**
 * Лимиты и политики import-governance для eventing snapshot.
 */
@Introspected
public record EventingLimits(
        int snapshotImportMaxEvents,
        boolean requireMatchingProcessedKeys,
        boolean rejectCrossListDuplicates,
        int maxDlqEvents,
        int maxProcessedEvents,
        long retentionSeconds
) {
}
