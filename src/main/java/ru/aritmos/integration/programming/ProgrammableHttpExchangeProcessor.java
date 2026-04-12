package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Кастомизируемая обработка outbound/inbound HTTP-запросов и ответов
 * для programmable-модуля (наружу к внешним службам и внутрь СУО).
 */
@Singleton
public class ProgrammableHttpExchangeProcessor {

    public static final String DIRECTION_OUTBOUND_EXTERNAL = "OUTBOUND_EXTERNAL";
    public static final String DIRECTION_INBOUND_SUO = "INBOUND_SUO";

    private final IntegrationGatewayConfiguration configuration;
    private final ObjectMapper objectMapper;

    public ProgrammableHttpExchangeProcessor(IntegrationGatewayConfiguration configuration,
                                             ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
    }

    public Map<String, String> enrichHeaders(Map<String, String> headers, String direction) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headers != null) {
            result.putAll(headers);
        }
        String normalizedDirection = normalizeDirection(direction);
        IntegrationGatewayConfiguration.HttpProcessingSettings settings = configuration.getProgrammableApi().getHttpProcessing();
        if (!settings.isEnabled()) {
            return result;
        }
        if (settings.isAddDirectionHeader()) {
            String headerName = settings.getDirectionHeaderName();
            if (headerName != null && !headerName.isBlank()) {
                result.put(headerName, normalizedDirection);
            }
        }
        return result;
    }

    public Map<String, Object> enrichBody(Map<String, Object> body, String direction) {
        String normalizedDirection = normalizeDirection(direction);
        IntegrationGatewayConfiguration.HttpProcessingSettings settings = configuration.getProgrammableApi().getHttpProcessing();
        Map<String, Object> payload = body == null ? Map.of() : body;
        if (!settings.isEnabled() || !settings.isRequestEnvelopeEnabled()) {
            return payload;
        }
        Map<String, Object> enriched = new LinkedHashMap<>();
        enriched.put("meta", Map.of(
                "direction", normalizedDirection,
                "processedAt", Instant.now().toString()
        ));
        enriched.put("data", payload);
        return enriched;
    }

    public Map<String, Object> processResponse(HttpResponse<String> response) {
        return processRawResponse(
                response.statusCode(),
                response.body(),
                response.headers().map(),
                DIRECTION_OUTBOUND_EXTERNAL
        );
    }

    public Map<String, Object> processRawResponse(int statusCode, String body, Map<String, List<String>> headers) {
        return processRawResponse(statusCode, body, headers, DIRECTION_OUTBOUND_EXTERNAL);
    }

    public Map<String, Object> processRawResponse(int statusCode, String body, Map<String, List<String>> headers, String direction) {
        IntegrationGatewayConfiguration.HttpProcessingSettings settings = configuration.getProgrammableApi().getHttpProcessing();
        String rawBody = body == null ? "" : body;
        String preview = truncate(rawBody, settings.getResponseBodyMaxChars());
        Object parsedJson = settings.isEnabled() && settings.isParseJsonBody()
                ? tryParseJson(rawBody)
                : null;
        String normalizedDirection = normalizeDirection(direction);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", statusCode);
        result.put("headers", headers == null ? Map.of() : headers);
        result.put("body", rawBody);
        result.put("bodyPreview", preview);
        if (settings.isEnabled()) {
            result.put("processingMeta", Map.of(
                    "direction", normalizedDirection,
                    "jsonParsed", parsedJson != null,
                    "previewTruncated", !rawBody.equals(preview)
            ));
        }
        if (settings.isEnabled() && settings.isParseJsonBody()) {
            result.put("json", parsedJson);
        }
        return result;
    }

    private Object tryParseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String truncate(String value, int maxChars) {
        int limit = maxChars <= 0 ? 1 : maxChars;
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...";
    }

    public List<String> supportedDirections() {
        return List.of(DIRECTION_OUTBOUND_EXTERNAL, DIRECTION_INBOUND_SUO);
    }

    public String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return DIRECTION_OUTBOUND_EXTERNAL;
        }
        String normalized = direction.trim().toUpperCase();
        return supportedDirections().contains(normalized) ? normalized : DIRECTION_OUTBOUND_EXTERNAL;
    }
}
