package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Единый формат ошибок внешнего API.
 */
@Introspected
@Schema(description = "Единый формат ошибки API.")
public record ErrorResponse(
        @Schema(description = "Машиночитаемый код ошибки.", example = "validation_error")
        String code,
        @Schema(description = "Сообщение об ошибке.", example = "Параметр branchId обязателен.")
        String message,
        @Schema(description = "HTTP статус ответа.", example = "400")
        int status,
        @Schema(description = "HTTP-метод запроса.", example = "POST")
        String method,
        @Schema(description = "Путь запроса.", example = "/api/v1/events/ingest")
        String path,
        @Schema(description = "Время формирования ошибки (UTC, ISO-8601).")
        Instant timestamp,
        @Schema(description = "Корреляционный идентификатор для трассировки.", example = "5c9d2f8b4fcb4cb2")
        String traceId
) {
}
