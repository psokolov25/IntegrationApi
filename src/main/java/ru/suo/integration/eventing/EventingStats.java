package ru.suo.integration.eventing;

import io.micronaut.core.annotation.Introspected;

/**
 * Снимок операционных метрик eventing-пайплайна (in-memory).
 */
@Introspected
public record EventingStats(
        long processedCount,
        long duplicateCount,
        long dlqCount,
        long replayCount,
        int inboxSize,
        int processedStoreSize,
        int dlqSize
) {
}
