package ru.suo.integration.eventing;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Оценка здоровья eventing-контура по in-memory метрикам.
 */
@Introspected
public record EventingHealth(
        String status,
        List<String> reasons,
        EventingStats stats
) {
}
