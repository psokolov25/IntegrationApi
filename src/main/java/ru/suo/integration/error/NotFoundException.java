package ru.suo.integration.error;

import io.micronaut.http.HttpStatus;

/**
 * Ошибка отсутствующего ресурса.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
