package ru.suo.integration.error;

import io.micronaut.http.HttpStatus;

/**
 * Ошибка конфликтного состояния.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super("CONFLICT", message, HttpStatus.CONFLICT);
    }
}
