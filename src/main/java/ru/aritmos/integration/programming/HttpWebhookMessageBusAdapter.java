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
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Адаптер публикации сообщений в HTTP webhook endpoint.
 */
@Singleton
public class HttpWebhookMessageBusAdapter implements CustomerMessageBusAdapter {

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int MAX_TIMEOUT_SECONDS = 120;
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
    public List<String> validateProperties(Map<String, String> properties) {
        List<String> violations = new java.util.ArrayList<>();
        try {
            normalizeMethod(properties == null ? null : properties.get("method"));
        } catch (IllegalArgumentException ex) {
            violations.add(ex.getMessage());
        }
        String timeoutRaw = properties == null ? null : properties.get("timeoutSeconds");
        if (timeoutRaw != null && !timeoutRaw.isBlank()) {
            try {
                int timeout = Integer.parseInt(timeoutRaw);
                if (timeout < 1 || timeout > MAX_TIMEOUT_SECONDS) {
                    violations.add("timeoutSeconds должен быть в диапазоне 1.." + MAX_TIMEOUT_SECONDS);
                }
            } catch (NumberFormatException ex) {
                violations.add("timeoutSeconds должен быть целым числом");
            }
        }
        if (properties != null) {
            properties.entrySet().stream()
                    .filter(entry -> entry.getKey() != null
                            && entry.getKey().regionMatches(true, 0, "header.", 0, "header.".length()))
                    .filter(entry -> entry.getKey().substring("header.".length()).trim().isBlank())
                    .forEach(entry -> violations.add("Некорректный header key '" + entry.getKey() + "': имя заголовка пустое"));
        }
        return violations;
    }

    @Override
    public Map<String, Object> publish(IntegrationGatewayConfiguration.MessageBrokerSettings broker,
                                       BrokerMessageRequest message) {
        String targetUrl = broker.getProperties().get("url");
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalArgumentException("Для WEBHOOK_HTTP требуется свойство broker.properties.url");
        }
        String method = normalizeMethod(broker.getProperties().get("method"));
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
        resolveAdditionalHeaders(broker.getProperties()).forEach(builder::header);

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
            int parsed = Integer.parseInt(raw);
            return Math.max(1, Math.min(parsed, MAX_TIMEOUT_SECONDS));
        } catch (NumberFormatException ex) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }

    private String normalizeMethod(String rawMethod) {
        if (rawMethod == null || rawMethod.isBlank()) {
            return "POST";
        }
        String normalized = rawMethod.trim().toUpperCase(Locale.ROOT);
        if (!List.of("POST", "PUT", "PATCH").contains(normalized)) {
            throw new IllegalArgumentException("Для WEBHOOK_HTTP поддерживаются только методы POST, PUT или PATCH");
        }
        return normalized;
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Payload webhook не сериализуется в JSON", ex);
        }
    }

    private Map<String, String> resolveAdditionalHeaders(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null || value.isBlank()) {
                continue;
            }
            if (!key.regionMatches(true, 0, "header.", 0, "header.".length())) {
                continue;
            }
            String headerName = key.substring("header.".length()).trim();
            if (headerName.isBlank() || "content-type".equals(headerName.toLowerCase(Locale.ROOT))) {
                continue;
            }
            headers.put(headerName, value);
        }
        return headers;
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
