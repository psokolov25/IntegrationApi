package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Расширенный health-ответ для liveness/readiness проверок.
 */
@Introspected
@Schema(description = "Структура health-ответа.")
public record HealthStatusResponse(
        @Schema(description = "Сводный статус компонента.", example = "UP")
        String status,
        @Schema(description = "Имя сервиса.", example = "integration-api")
        String service,
        @Schema(description = "Момент времени формирования ответа.")
        Instant timestamp,
        @Schema(description = "Статусы подкомпонентов и режимов.")
        Map<String, String> components
) {
}
