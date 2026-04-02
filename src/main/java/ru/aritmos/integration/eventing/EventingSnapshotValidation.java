package ru.aritmos.integration.eventing;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Результат проверки snapshot перед import.
 */
@Introspected
public record EventingSnapshotValidation(
        boolean valid,
        int processedEvents,
        int dlqEvents,
        int totalEvents,
        List<EventingSnapshotViolation> violations
) {
}
