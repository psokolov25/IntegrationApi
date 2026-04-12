package ru.aritmos.integration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Запрос на расчет уже доступных услуг с учетом пройденных клиентом этапов.
 */
@Schema(description = "Расчет доступных услуг по клиенту: на вход передаются уже пройденные услуги и каталог услуг с зависимостями.")
public record CustomerServiceAvailabilityRequest(
        @Schema(description = "Параметры обращения к CRM/МИС для загрузки каталога услуг (если serviceCatalog не передан).")
        CustomerMedicalServicesRequest medicalServicesRequest,
        @Schema(description = "Идентификатор клиента (для трассировки расчета в ответе).", example = "customer-123")
        String customerId,
        @Schema(description = "Список serviceId/code услуг, которые клиент уже прошел.", implementation = Object.class)
        List<String> completedServiceIds,
        @Schema(description = "Опциональный список услуг-кандидатов для проверки. Если не задан, проверяются все услуги каталога.", implementation = Object.class)
        List<String> candidateServiceIds,
        @Schema(description = "Опциональный каталог услуг с зависимостями (если задан, внешний вызов в CRM/МИС не выполняется).", implementation = Object.class)
        List<Map<String, Object>> serviceCatalog
) {
}
