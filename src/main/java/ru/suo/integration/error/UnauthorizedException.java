package ru.suo.integration.error;

import io.micronaut.http.HttpStatus;

/**
 * Ошибка неаутентифицированного доступа.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }
}
