package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Команда обновления состояния отделения для кастомных внешних пультов.
 */
@Introspected
public record BranchStateUpdateRequest(
        @NotBlank String status,
        @NotBlank String activeWindow,
        @Min(0) int queueSize,
        @NotBlank String updatedBy
) {
}
