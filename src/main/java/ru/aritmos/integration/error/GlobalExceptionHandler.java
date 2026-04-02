package ru.aritmos.integration.error;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import ru.aritmos.integration.domain.ErrorResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * Глобальный обработчик ошибок с унифицированным форматом ответа.
 */
@Produces
@Singleton
public class GlobalExceptionHandler implements ExceptionHandler<Exception, HttpResponse<ErrorResponse>> {

    @Override
    public HttpResponse<ErrorResponse> handle(HttpRequest request, Exception exception) {
        HttpStatus status = resolveStatus(exception);
        String code = resolveCode(exception);
        String message = resolveMessage(exception);
        String traceId = request.getHeaders().contains("X-Request-Id")
                ? request.getHeaders().get("X-Request-Id")
                : UUID.randomUUID().toString();
        ErrorResponse body = new ErrorResponse(
                code,
                message,
                status.getCode(),
                request.getMethodName(),
                request.getPath(),
                Instant.now(),
                traceId
        );
        return HttpResponse.status(status).body(body);
    }

    private HttpStatus resolveStatus(Exception exception) {
        if (exception instanceof ApiException apiException) {
            return apiException.status();
        }
        if (exception instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (exception instanceof SecurityException) {
            return HttpStatus.FORBIDDEN;
        }
        if (exception instanceof IllegalStateException) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveCode(Exception exception) {
        if (exception instanceof ApiException apiException) {
            return apiException.code();
        }
        if (exception instanceof IllegalArgumentException) {
            return "BAD_REQUEST";
        }
        if (exception instanceof SecurityException) {
            return "FORBIDDEN";
        }
        if (exception instanceof IllegalStateException) {
            return "CONFLICT";
        }
        return "INTEGRATION_ERROR";
    }

    private String resolveMessage(Exception exception) {
        if (exception instanceof ApiException) {
            return exception.getMessage();
        }
        if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
            return exception.getMessage();
        }
        if (exception instanceof SecurityException) {
            return "Доступ запрещен";
        }
        return "Внутренняя ошибка сервиса";
    }
}
