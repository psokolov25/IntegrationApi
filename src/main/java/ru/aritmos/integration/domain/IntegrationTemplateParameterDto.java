package ru.aritmos.integration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Параметр integration template (ITS) для импорта/экспорта обработчиков.
 */
@Schema(description = "Параметр integration template: структура и значение по умолчанию")
public record IntegrationTemplateParameterDto(
        @Schema(description = "Ключ параметра для подстановки в Groovy-шаблон", example = "vmBaseUrl")
        String key,
        @Schema(description = "Человекочитаемое имя параметра", example = "Базовый URL VisitManager")
        String label,
        @Schema(description = "Подсказка для оператора в GUI", example = "URL сервиса VisitManager для REST-вызовов")
        String description,
        @Schema(description = "Обязателен ли параметр при импорте")
        boolean required,
        @Schema(description = "Значение по умолчанию")
        String defaultValue
) {
}
