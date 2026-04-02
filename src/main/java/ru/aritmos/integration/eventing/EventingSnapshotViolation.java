package ru.aritmos.integration.eventing;

import io.micronaut.core.annotation.Introspected;

/**
 * Детализация нарушения в snapshot-validation.
 */
@Introspected
public record EventingSnapshotViolation(
        String code,
        String path,
        String message
) {
}
