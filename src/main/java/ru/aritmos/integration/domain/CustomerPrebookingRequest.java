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
        @Schema(description = "Путь CRM API для получения списка услуг в универсальном сценарии (если пусто, используется path или /api/v1/medical-services/available)", example = "/api/v2/services/available")
        String servicesPath,
        @Schema(description = "Путь CRM API для получения временных окон в универсальном сценарии (если пусто, используется path или /api/v1/pre-appointments)", example = "/api/v2/prebookings/time-windows")
        String timeWindowsPath,
        @Schema(description = "Режим выборки prebooking: LEGACY_PREBOOKING (по умолчанию, stage-1 совместимость), TIME_WINDOWS, SERVICES_FLAT, SERVICES_WITH_PREREQUISITES, TIME_WINDOWS_AND_SERVICES, TIME_WINDOWS_AND_SERVICES_WITH_PREREQUISITES", example = "TIME_WINDOWS_AND_SERVICES_WITH_PREREQUISITES")
        String queryMode,
        @Schema(description = "Идентификатор целевой услуги для поиска слотов (опционально)", example = "THERAPIST")
        String targetServiceId,
        @Schema(description = "Идентификатор филиала для отбора услуг/окон (опционально)", example = "BR-1")
        String branchId,
        @Schema(description = "Дополнительные фильтры", implementation = Object.class)
        Map<String, Object> filters,
        @Schema(description = "Дополнительные HTTP-заголовки", implementation = Object.class)
        Map<String, String> headers,
        @Schema(description = "Тип коннектора: REST_API (по умолчанию), DATA_BUS, MESSAGE_BROKER", example = "MESSAGE_BROKER")
        String connectorType,
        @Schema(description = "Идентификатор брокера/шины для DATA_BUS или MESSAGE_BROKER", example = "customer-prebooking-bus")
        String brokerId,
        @Schema(description = "Топик/канал публикации для DATA_BUS или MESSAGE_BROKER", example = "crm.prebooking")
        String topic,
        @Schema(description = "Ключ сообщения для DATA_BUS или MESSAGE_BROKER", example = "prebooking-001")
        String messageKey,
        @Schema(description = "ID Groovy-скрипта постобработки ответа (опционально)", example = "crm-prebooking-response-transform")
        String responseScriptId,
        @Schema(description = "Параметры для Groovy-скрипта постобработки", implementation = Object.class)
        Map<String, Object> responseScriptParameters,
        @Schema(description = "Контекст для Groovy-скрипта постобработки", implementation = Object.class)
        Map<String, Object> responseScriptContext
) {
}
