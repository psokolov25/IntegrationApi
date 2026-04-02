package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;

import java.time.Instant;

/**
 * Единый формат ошибок внешнего API.
 */
@Introspected
public record ErrorResponse(
        String code,
        String message,
        int status,
        String method,
        String path,
        Instant timestamp,
        String traceId
) {
}
