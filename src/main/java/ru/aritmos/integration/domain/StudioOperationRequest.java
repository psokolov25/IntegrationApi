package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Запрос на выполнение служебной операции programmable-студии.
 */
@Introspected
@Schema(description = "Запрос на выполнение операции programmable-студии")
public record StudioOperationRequest(
        @Schema(description = "Код операции", example = "FLUSH_OUTBOX")
        String operation,
        @Schema(description = "Дополнительные параметры операции (например limit/scriptId)")
        Map<String, Object> parameters
) {
}
