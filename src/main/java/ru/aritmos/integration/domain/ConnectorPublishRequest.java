package ru.aritmos.integration.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Запрос на публикацию сообщения в брокер/шину заказчика.
 */
@Schema(description = "Команда ручной публикации сообщения в настроенный брокер/шину заказчика.")
public record ConnectorPublishRequest(
        @Schema(description = "Идентификатор брокера из integration.programmable-api.message-brokers[*].id", example = "customer-databus")
        String brokerId,
        @Schema(description = "Topic/канал назначения", example = "customer.branch.events")
        String topic,
        @Schema(description = "Опциональный key сообщения", example = "BR-501")
        String key,
        @Schema(description = "Payload сообщения", implementation = Object.class)
        Map<String, Object> payload,
        @Schema(description = "Дополнительные headers", implementation = Object.class)
        Map<String, String> headers
) {
}
