package ru.suo.integration.domain;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;

/**
 * Запрос на получение internal access token.
 */
@Introspected
public record InternalTokenRequest(@NotBlank String clientId, @NotBlank String clientSecret) {
}
