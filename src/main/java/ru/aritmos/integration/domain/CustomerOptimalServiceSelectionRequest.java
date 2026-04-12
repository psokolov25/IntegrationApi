package ru.aritmos.integration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Запрос на выбор оптимальной услуги из списка кандидатов.
 */
@Schema(description = "Запрос на выбор оптимальной медицинской услуги по клиенту с поддержкой кастомной Groovy-логики.")
public record CustomerOptimalServiceSelectionRequest(
        @Schema(description = "Идентификатор клиента для трассировки выбора.", example = "customer-1001")
        String customerId,
        @Schema(description = "Список услуг-кандидатов. Для каждой услуги поддерживаются поля serviceId/serviceName и метрики очереди (waitingCount, standardWaitMinutes).", implementation = Object.class)
        List<Map<String, Object>> services,
        @Schema(description = "ID Groovy-скрипта (тип OPTIMAL_SERVICE_SELECTION) для кастомного выбора услуги. Если не задан, используется встроенный алгоритм минимизации waitingCount*standardWaitMinutes.", example = "optimal-service-selector")
        String selectionScriptId,
        @Schema(description = "Параметры выполнения Groovy-скрипта выбора.", implementation = Object.class)
        Map<String, Object> selectionScriptParameters,
        @Schema(description = "Контекст выполнения Groovy-скрипта выбора.", implementation = Object.class)
        Map<String, Object> selectionScriptContext
) {
}
