package ru.aritmos.integration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Описание скрипта-обработчика внутри integration template.
 */
@Schema(description = "Скрипт-обработчик, входящий в integration template")
public record IntegrationTemplateScriptDto(
        @Schema(description = "Идентификатор скрипта внутри Integration API", example = "visit-created-handler")
        String scriptId,
        @Schema(description = "Тип programmable-скрипта", example = "MESSAGE_BUS_REACTION")
        String type,
        @Schema(description = "Описание назначения скрипта", example = "Реакция на входящие события VISIT_CREATED")
        String description,
        @Schema(description = "Путь к groovy-файлу внутри ITS-архива", example = "scripts/visit-created-handler.groovy")
        String file
) {
}
