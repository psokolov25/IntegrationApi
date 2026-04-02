package ru.suo.integration.error;

import io.micronaut.http.HttpStatus;

/**
 * Базовое API-исключение с управляемым HTTP-статусом и кодом.
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
