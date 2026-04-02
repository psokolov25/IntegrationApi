package ru.suo.integration.eventing;

import io.micronaut.core.annotation.Introspected;

/**
 * Декларация включенных возможностей eventing API.
 */
@Introspected
public record EventingCapabilities(
        boolean ingestion,
        boolean replay,
        boolean dlqOperations,
        boolean maintenance,
        boolean snapshotExportImport,
        boolean healthChecks
) {
}
