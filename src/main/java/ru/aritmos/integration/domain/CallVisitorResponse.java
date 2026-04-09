package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Результат вызова посетителя в VisitManager.
 */
@Introspected
@Schema(description = "Результат вызова посетителя.")
public record CallVisitorResponse(
        @Schema(description = "Идентификатор визита.", example = "VIS-7788")
        String visitId,
        @Schema(description = "Итоговый статус операции вызова.", example = "CALLED")
        String status,
        @Schema(description = "Идентификатор VisitManager, обработавшего операцию.", example = "vm-main")
        String sourceVisitManagerId
) {
}
