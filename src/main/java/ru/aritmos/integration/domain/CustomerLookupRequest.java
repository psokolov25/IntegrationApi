package ru.aritmos.integration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Универсальный запрос на поиск/идентификацию клиента во внешней CRM заказчика.
 */
@Schema(description = "Параметры поиска клиента во внешней CRM по идентификационной строке (телефон/СНИЛС/ИНН и т.д.).")
public record CustomerLookupRequest(
        @Schema(description = "Идентификатор внешнего REST-коннектора CRM", example = "customer-crm")
        String serviceId,
        @Schema(description = "Тип идентификатора клиента (PHONE/SNILS/INN/POLICY/CUSTOM)", example = "PHONE")
        String identifierType,
        @Schema(description = "Строка идентификатора клиента", example = "+79991234567")
        String identifierValue,
        @Schema(description = "Путь CRM API для идентификации (если пусто, используется /api/v1/customers/identify)", example = "/api/v2/patients/find")
        String path,
        @Schema(description = "Дополнительные фильтры поиска", implementation = Object.class)
        Map<String, Object> filters,
        @Schema(description = "Дополнительные HTTP-заголовки", implementation = Object.class)
        Map<String, String> headers,
        @Schema(description = "Тип коннектора: REST_API (по умолчанию), DATA_BUS, MESSAGE_BROKER", example = "REST_API")
        String connectorType,
        @Schema(description = "Идентификатор брокера/шины для DATA_BUS или MESSAGE_BROKER", example = "customer-databus")
        String brokerId,
        @Schema(description = "Топик/канал публикации для DATA_BUS или MESSAGE_BROKER", example = "crm.identify-client")
        String topic,
        @Schema(description = "Ключ сообщения для DATA_BUS или MESSAGE_BROKER", example = "customer-lookup-001")
        String messageKey,
        @Schema(description = "ID Groovy-скрипта постобработки ответа (опционально)", example = "crm-identify-response-transform")
        String responseScriptId,
        @Schema(description = "Параметры для Groovy-скрипта постобработки", implementation = Object.class)
        Map<String, Object> responseScriptParameters,
        @Schema(description = "Контекст для Groovy-скрипта постобработки", implementation = Object.class)
        Map<String, Object> responseScriptContext
) {
}
