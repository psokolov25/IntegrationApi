package ru.aritmos.integration.domain;

import java.util.Map;

/**
 * Запрос на обработку входящего сообщения от шины/брокера заказчика.
 */
public record InboundMessageReactionRequest(
        String brokerId,
        String topic,
        String key,
        Map<String, Object> payload,
        Map<String, String> headers,
        String scriptId
) {
}
