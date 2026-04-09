package ru.aritmos.integration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Результат предпросмотра ITS-архива перед импортом в programmable handlers.
 */
@Schema(description = "Предпросмотр import-архива ITS: скрипты, параметры и метаданные")
public record IntegrationTemplatePreviewDto(
        @Schema(description = "Идентификатор шаблона", example = "visitmanager-core")
        String templateId,
        @Schema(description = "Название шаблона", example = "VisitManager core templates")
        String name,
        @Schema(description = "Описание шаблона")
        String description,
        @Schema(description = "Версия шаблона", example = "1.0.0")
        String version,
        @Schema(description = "Список параметров для подстановки")
        List<IntegrationTemplateParameterDto> parameters,
        @Schema(description = "Скрипты, входящие в архив")
        List<IntegrationTemplateScriptDto> scripts
) {
}
