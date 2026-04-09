package ru.aritmos.integration.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Расширенный запрос на выполнение/отладку Groovy-скрипта с явной передачей параметров.
 */
@Schema(description = "Запрос выполнения скрипта: payload + параметры + контекст")
public record ScriptExecutionRequest(
        @Schema(description = "Бизнес payload для логики скрипта")
        JsonNode payload,
        @Schema(description = "Параметры выполнения (100% передаются в binding как params/parameters)")
        Map<String, Object> parameters,
        @Schema(description = "Дополнительный контекст выполнения (например correlationId, origin, stage)")
        Map<String, Object> context
) {
}
