package ru.aritmos.integration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Запрос на получение перечня доступных медицинских услуг по идентификатору клиента.
 */
@Schema(description = "Параметры запроса доступных медицинских услуг по клиенту во внешней CRM/МИС.")
public record CustomerMedicalServicesRequest(
        @Schema(description = "Идентификатор внешнего REST-коннектора CRM/МИС", example = "customer-crm")
        String serviceId,
        @Schema(description = "Тип идентификатора клиента (PHONE/SNILS/INN/POLICY/CUSTOM)", example = "SNILS")
        String identifierType,
        @Schema(description = "Строка идентификатора клиента", example = "123-456-789 00")
        String identifierValue,
        @Schema(description = "Путь CRM API для получения услуг (если пусто, используется /api/v1/medical-services/available)", example = "/api/v2/services/available")
        String path,
        @Schema(description = "Ограничение по кодам подразделений/филиалов (опционально)", implementation = Object.class)
        List<String> branchIds,
        @Schema(description = "Дополнительные фильтры запроса", implementation = Object.class)
        Map<String, Object> filters,
        @Schema(description = "Дополнительные HTTP-заголовки", implementation = Object.class)
        Map<String, String> headers,
        @Schema(description = "Тип коннектора: REST_API (по умолчанию), DATA_BUS, MESSAGE_BROKER", example = "DATA_BUS")
        String connectorType,
        @Schema(description = "Идентификатор брокера/шины для DATA_BUS или MESSAGE_BROKER", example = "customer-databus")
        String brokerId,
        @Schema(description = "Топик/канал публикации для DATA_BUS или MESSAGE_BROKER", example = "crm.medical-services")
        String topic,
        @Schema(description = "Ключ сообщения для DATA_BUS или MESSAGE_BROKER", example = "medical-services-001")
        String messageKey,
        @Schema(description = "ID Groovy-скрипта постобработки ответа (опционально)", example = "crm-services-response-transform")
        String responseScriptId,
        @Schema(description = "Параметры для Groovy-скрипта постобработки", implementation = Object.class)
        Map<String, Object> responseScriptParameters,
        @Schema(description = "Контекст для Groovy-скрипта постобработки", implementation = Object.class)
        Map<String, Object> responseScriptContext
) {
}
