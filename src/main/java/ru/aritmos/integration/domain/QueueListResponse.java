package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Ответ списка очередей по филиалу.
 */
@Introspected
@Schema(description = "Ответ со списком очередей конкретного отделения.")
public record QueueListResponse(
        @Schema(description = "Идентификатор отделения.", example = "BR-001")
        String branchId,
        @Schema(description = "Идентификатор VisitManager-источника.", example = "vm-main")
        String sourceVisitManagerId,
        @Schema(description = "Нормализованный список очередей.")
        List<QueueItemDto> queues,
        @Schema(description = "Признак, что данные выданы из кэша.")
        boolean cached
) {
}
