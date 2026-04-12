package ru.aritmos.integration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Запрос на получение данных о предварительной записи клиента.
 */
@Schema(description = "Параметры запроса предварительной записи клиента (pre-booking) во внешней CRM/МИС.")
public record CustomerPrebookingRequest(
        @Schema(description = "Идентификатор внешнего REST-коннектора CRM/МИС", example = "customer-crm")
        String serviceId,
        @Schema(description = "Тип идентификатора клиента (PHONE/SNILS/INN/POLICY/CUSTOM)", example = "INN")
        String identifierType,
        @Schema(description = "Строка идентификатора клиента", example = "7707083893")
        String identifierValue,
        @Schema(description = "Путь CRM API для предварительной записи (если пусто, используется /api/v1/pre-appointments)", example = "/api/v2/prebookings/search")
        String path,
        @Schema(description = "Дополнительные фильтры", implementation = Object.class)
        Map<String, Object> filters,
        @Schema(description = "Дополнительные HTTP-заголовки", implementation = Object.class)
        Map<String, String> headers
) {
}

