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
        Map<String, String> headers
) {
}

