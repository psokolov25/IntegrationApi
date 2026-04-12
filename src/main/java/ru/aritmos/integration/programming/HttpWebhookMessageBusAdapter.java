package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Адаптер публикации сообщений в HTTP webhook endpoint.
 */
@Singleton
public class HttpWebhookMessageBusAdapter implements CustomerMessageBusAdapter {

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final List<String> SUPPORTED_TYPES = List.of("WEBHOOK_HTTP", "HTTP_WEBHOOK", "WEBHOOK", "WEBHOOK_JSON");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
            .build();

    public HttpWebhookMessageBusAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String brokerType) {
        if (brokerType == null || brokerType.isBlank()) {
            return false;
        }
        String normalized = brokerType.trim().toUpperCase();
        return SUPPORTED_TYPES.contains(normalized);
    }

    @Override
    public List<String> supportedBrokerTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public List<Map<String, Object>> supportedBrokerProfiles() {
        return List.of(
                Map.of(
                        "type", "WEBHOOK_HTTP",
                        "description", "HTTP webhook публикация JSON payload в шлюз/ESB заказчика",
                        "adapterMode", "HTTP_DELIVERY",
                        "requiredProperties", List.of("url"),
                        "optionalProperties", List.of("method", "timeoutSeconds", "header.Authorization"),
                        "propertyTemplate", Map.of(
                                "url", "https://gateway.customer.local/integration/events",
                                "method", "POST",
                                "timeoutSeconds", "10",
                                "header.Authorization", "Bearer <token>"
                        )
                )
        );
    }

    @Override
    public Map<String, Object> publish(IntegrationGatewayConfiguration.MessageBrokerSettings broker,
                                       BrokerMessageRequest message) {
        String targetUrl = broker.getProperties().get("url");
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalArgumentException("Для WEBHOOK_HTTP требуется свойство broker.properties.url");
        }
        String method = broker.getProperties().getOrDefault("method", "POST").toUpperCase();
        int timeoutSeconds = parseTimeout(broker.getProperties().get("timeoutSeconds"));
        String payloadJson = writeJson(Map.of(
                "topic", message.topic(),
                "key", message.key() == null ? "" : message.key(),
                "payload", message.payload() == null ? Map.of() : message.payload(),
                "headers", message.headers() == null ? Map.of() : message.headers(),
                "sentAt", Instant.now().toString()
        ));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json");

        HttpRequest request = switch (method) {
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(payloadJson)).build();
            case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(payloadJson)).build();
            default -> builder.POST(HttpRequest.BodyPublishers.ofString(payloadJson)).build();
        };
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return Map.of(
                    "brokerId", broker.getId(),
                    "brokerType", broker.getType(),
                    "topic", message.topic(),
                    "status", response.statusCode() >= 200 && response.statusCode() < 300 ? "DELIVERED" : "FAILED",
                    "httpStatus", response.statusCode(),
                    "responseBody", truncate(response.body(), 2000),
                    "timestamp", Instant.now().toString()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Ошибка отправки webhook-сообщения", ex);
        }
    }

    private int parseTimeout(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Payload webhook не сериализуется в JSON", ex);
        }
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }
}
