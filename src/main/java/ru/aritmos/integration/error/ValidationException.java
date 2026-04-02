package ru.aritmos.integration.error;

import io.micronaut.http.HttpStatus;

/**
 * Ошибка валидации входных данных.
 */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }
}
