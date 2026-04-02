package ru.suo.integration.eventing;

import io.micronaut.core.annotation.Introspected;

/**
 * Расширенный анализ import snapshot (dry-run).
 */
@Introspected
public record EventingImportAnalysis(
        boolean valid,
        boolean strictPolicies,
        boolean clearBeforeImport,
        int importedProcessed,
        int importedDlq,
        int totalImported,
        int limitUsagePercent,
        boolean projectedProcessedOverflow,
        boolean projectedDlqOverflow,
        EventingStats projectedStats,
        EventingSnapshotValidation validation
) {
}
