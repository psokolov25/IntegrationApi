package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Состояние отделения в VisitManager для внешних пультов/приемной.
 */
@Introspected
@Schema(description = "Текущее состояние отделения.")
public record BranchStateDto(
        @Schema(description = "Идентификатор отделения.", example = "BR-001")
        String branchId,
        @Schema(description = "Идентификатор VisitManager-источника.", example = "vm-main")
        String sourceVisitManagerId,
        @Schema(description = "Статус отделения.", example = "OPEN")
        String status,
        @Schema(description = "Активное окно обслуживания.", example = "09:00-18:00")
        String activeWindow,
        @Schema(description = "Число посетителей в очередях.", example = "4")
        int queueSize,
        @Schema(description = "Время последнего обновления состояния.")
        Instant updatedAt,
        @Schema(description = "Признак, что данные взяты из кэша.")
        boolean cached,
        @Schema(description = "Идентификатор пользователя/системы, выполнившей изменение.", example = "system:eventing")
        String updatedBy
) {
}
