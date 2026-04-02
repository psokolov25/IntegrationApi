package ru.suo.integration.domain;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;

/**
 * Команда вызова посетителя.
 */
@Introspected
public record CallVisitorRequest(
        @NotBlank String branchId,
        @NotBlank String queueId,
        @NotBlank String operatorId,
        @NotBlank String idempotencyKey
) {
}
