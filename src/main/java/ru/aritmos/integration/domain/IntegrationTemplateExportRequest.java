package ru.aritmos.integration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Запрос на экспорт programmable-обработчиков в ITS-архив.
 */
@Schema(description = "Параметры экспорта programmable-обработчиков в ITS-архив")
public record IntegrationTemplateExportRequest(
        @Schema(description = "Идентификатор шаблона", example = "custom-branch-sync")
        String templateId,
        @Schema(description = "Название шаблона", example = "Кастомная синхронизация branch-state")
        String name,
        @Schema(description = "Описание шаблона")
        String description,
        @Schema(description = "Список scriptId для включения в архив")
        List<String> scriptIds,
        @Schema(description = "Значения по умолчанию для параметров (key -> defaultValue)")
        Map<String, String> parameterDefaults
) {
}
