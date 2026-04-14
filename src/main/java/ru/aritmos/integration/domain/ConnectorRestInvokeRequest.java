package ru.aritmos.integration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Запрос на вызов внешнего REST-сервиса заказчика.
 */
@Schema(description = "Команда ручного вызова внешнего REST-сервиса из configured connectors.")
public record ConnectorRestInvokeRequest(
        @Schema(description = "Идентификатор сервиса из integration.programmable-api.external-rest-services[*].id", example = "customer-crm")
        String serviceId,
        @Schema(description = "HTTP метод", example = "POST")
        String method,
        @Schema(description = "Путь относительно baseUrl", example = "/api/events")
        String path,
        @Schema(description = "JSON body", implementation = Object.class)
        Map<String, Object> body,
        @Schema(description = "Дополнительные headers", implementation = Object.class)
        Map<String, String> headers
) {
}
