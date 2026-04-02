package ru.suo.integration.domain;

import io.micronaut.core.annotation.Introspected;

/**
 * Метрика вызовов по конкретной инсталляции VisitManager.
 */
@Introspected
public record VisitManagerMetricDto(String target, long successCount, long errorCount) {
}
