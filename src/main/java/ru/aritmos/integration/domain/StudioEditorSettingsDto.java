package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Персональные настройки IDE-редактора programmable-студии.
 */
@Introspected
@Schema(description = "Настройки IDE-редактора programmable-студии")
public record StudioEditorSettingsDto(
        @Schema(description = "Тема редактора", example = "dark")
        String theme,
        @Schema(description = "Размер шрифта", example = "14")
        int fontSize,
        @Schema(description = "Автоматическое сохранение скрипта")
        boolean autoSave,
        @Schema(description = "Перенос длинных строк")
        boolean wordWrap,
        @Schema(description = "Последний открытый scriptId", example = "branch-state-view")
        String lastScriptId,
        @Schema(description = "Время последнего обновления настроек")
        Instant updatedAt
) {
}
