package ru.suo.integration.domain;

import io.micronaut.core.annotation.Introspected;

import java.time.Instant;
import java.util.Map;

/**
 * Расширенный health-ответ для liveness/readiness проверок.
 */
@Introspected
public record HealthStatusResponse(
        String status,
        String service,
        Instant timestamp,
        Map<String, String> components
) {
}
