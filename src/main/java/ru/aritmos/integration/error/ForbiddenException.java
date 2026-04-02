package ru.aritmos.integration.error;

import io.micronaut.http.HttpStatus;

/**
 * Ошибка доступа по правам.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}
