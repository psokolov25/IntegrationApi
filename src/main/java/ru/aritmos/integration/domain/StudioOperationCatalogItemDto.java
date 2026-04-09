package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Элемент каталога поддерживаемых studio-операций.
 *
 * @param operation код операции
 * @param description человеко-читаемое описание операции
 * @param parameterTemplate шаблон параметров для UI
 */
@Introspected
@Schema(description = "Элемент каталога поддерживаемых studio-операций")
public record StudioOperationCatalogItemDto(
                                            @Schema(description = "Код операции", example = "FLUSH_OUTBOX")
                                            String operation,
                                            @Schema(description = "Человекочитаемое описание операции")
                                            String description,
                                            @Schema(description = "Шаблон параметров для UI")
                                            Map<String, Object> parameterTemplate) {
}
