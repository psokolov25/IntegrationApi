package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Агрегированный ответ по нескольким филиалам с partial availability.
 */
@Introspected
@Schema(description = "Агрегированный результат запросов очередей по нескольким отделениям.")
public record AggregatedQueuesResponse(
        @Schema(description = "Успешно полученные ответы по отделениям.")
        List<QueueListResponse> successful,
        @Schema(description = "Ошибки по отделениям, которые не удалось получить.")
        List<BranchError> failed,
        @Schema(description = "Признак частичной деградации (partial availability).")
        boolean partial
) {
    @Introspected
    @Schema(description = "Ошибка агрегации для конкретного отделения.")
    public record BranchError(
            @Schema(description = "Идентификатор отделения.", example = "BR-404")
            String branchId,
            @Schema(description = "Целевой VisitManager.", example = "vm-backup")
            String target,
            @Schema(description = "Текст ошибки.", example = "Таймаут при обращении к VisitManager")
            String message
    ) {
    }
}
