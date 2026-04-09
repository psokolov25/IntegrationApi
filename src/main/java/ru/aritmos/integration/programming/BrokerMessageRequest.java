package ru.aritmos.integration.programming;

import java.util.Map;

/**
 * Сообщение для отправки в брокер/шину данных.
 */
public record BrokerMessageRequest(
        String topic,
        String key,
        Map<String, Object> payload,
        Map<String, String> headers
) {
}
