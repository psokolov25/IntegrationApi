package ru.suo.integration.domain;

import io.micronaut.core.annotation.Introspected;

/**
 * Ответ выдачи internal access token.
 */
@Introspected
public record InternalTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
