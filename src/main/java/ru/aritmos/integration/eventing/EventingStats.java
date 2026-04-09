package ru.aritmos.integration.eventing;

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
        int inboxInProgressSize,
        int processedStoreSize,
        int dlqSize,
        int outboxSize,
        int outboxPendingSize,
        int outboxFailedSize,
        int outboxDeadSize
) {
}
