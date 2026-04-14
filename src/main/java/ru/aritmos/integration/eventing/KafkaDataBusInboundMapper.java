package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Маппер raw Kafka/DataBus payload в каноническую модель IntegrationEvent.
 */
public class KafkaDataBusInboundMapper {

    private final ObjectMapper objectMapper;

    public KafkaDataBusInboundMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IntegrationEvent map(String rawPayload, String sourceTopic) {
        return map(rawPayload, sourceTopic, -1, -1);
    }

    public IntegrationEvent map(String rawPayload, String sourceTopic, int partition, long offset) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventId = text(root, "eventId", "id", "meta.eventId", "data.meta.eventId");
            String eventType = text(root, "eventType", "type", "meta.eventType", "data.meta.eventType");
            String source = text(root, "source", "meta.source", "data.meta.source");
            String occurredAtRaw = text(root, "occurredAt", "timestamp", "meta.occurredAt", "data.meta.occurredAt");
            JsonNode payloadNode = root.path("payload");
            if (payloadNode.isMissingNode() || payloadNode.isNull()) {
                payloadNode = root.path("data");
            }
            if (payloadNode.isMissingNode() || payloadNode.isNull()) {
                payloadNode = root;
            }
            Map<String, Object> payload = objectMapper.convertValue(payloadNode, Map.class);
            return new IntegrationEvent(
                    emptyToSynthetic(eventId, sourceTopic, eventType, partition, offset),
                    isBlank(eventType) ? "unknown" : eventType,
                    isBlank(source) ? "kafka-databus" : source,
                    parseInstant(occurredAtRaw),
                    payload
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("Некорректный Kafka/DataBus payload: " + ex.getMessage(), ex);
        }
    }

    private Instant parseInstant(String raw) {
        if (isBlank(raw)) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            return Instant.now();
        }
    }

    private String emptyToSynthetic(String eventId,
                                    String sourceTopic,
                                    String eventType,
                                    int partition,
                                    long offset) {
        if (!isBlank(eventId)) {
            return eventId;
        }
        String type = isBlank(eventType) ? "unknown" : eventType;
        if (partition >= 0 && offset >= 0) {
            return sourceTopic + ":" + partition + ":" + offset;
        }
        return sourceTopic + ":" + type + ":synthetic";
    }

    private String text(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode node = root;
            String[] segments = path.split("\\.");
            boolean found = true;
            for (String segment : segments) {
                node = node.path(segment);
                if (node.isMissingNode() || node.isNull()) {
                    found = false;
                    break;
                }
            }
            if (found && node.isValueNode()) {
                String value = node.asText();
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
