package ru.aritmos.integration.error;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.domain.ErrorResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnUnauthorizedForApiException() {
        HttpRequest<?> request = HttpRequest.GET("/api/v2/events/dlq");
        var response = handler.handle(request, new UnauthorizedException("Нет авторизации"));

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.status());
        ErrorResponse body = response.body();
        Assertions.assertNotNull(body);
        Assertions.assertEquals("UNAUTHORIZED", body.code());
        Assertions.assertEquals(401, body.status());
        Assertions.assertEquals("GET", body.method());
    }

    @Test
    void shouldHideMessageForUnexpectedException() {
        HttpRequest<?> request = HttpRequest.GET("/health");
        var response = handler.handle(request, new RuntimeException("secret details"));

        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status());
        ErrorResponse body = response.body();
        Assertions.assertNotNull(body);
        Assertions.assertEquals("INTEGRATION_ERROR", body.code());
        Assertions.assertEquals("Внутренняя ошибка сервиса", body.message());
    }
}
