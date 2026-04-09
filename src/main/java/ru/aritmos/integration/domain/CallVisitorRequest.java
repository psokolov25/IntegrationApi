package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Команда вызова посетителя.
 */
@Introspected
@Schema(description = "Команда вызова посетителя.")
public record CallVisitorRequest(
        @Schema(description = "Идентификатор отделения.", example = "BR-001")
        @NotBlank String branchId,
        @Schema(description = "Идентификатор очереди.", example = "Q-101")
        @NotBlank String queueId,
        @Schema(description = "Идентификатор оператора.", example = "operator-17")
        @NotBlank String operatorId,
        @Schema(description = "Ключ идемпотентности операции.", example = "9aa878b7-3afe-4f53-8488-a9f9357f95e0")
        @NotBlank String idempotencyKey
) {
}
