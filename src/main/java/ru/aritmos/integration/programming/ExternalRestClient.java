package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Программируемый REST-клиент для внешних сервисов заказчика.
 */
@Singleton
public class ExternalRestClient {

    private final IntegrationGatewayConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final ProgrammableHttpExchangeProcessor exchangeProcessor;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ExternalRestClient(IntegrationGatewayConfiguration configuration,
                              ObjectMapper objectMapper,
                              ProgrammableHttpExchangeProcessor exchangeProcessor) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
        this.exchangeProcessor = exchangeProcessor;
    }

    ExternalRestClient(IntegrationGatewayConfiguration configuration,
                       ObjectMapper objectMapper) {
        this(configuration, objectMapper, new ProgrammableHttpExchangeProcessor(configuration, objectMapper));
    }

    public Map<String, Object> invoke(String serviceId,
                                      String method,
                                      String path,
                                      Map<String, Object> body,
                                      Map<String, String> headers) {
        IntegrationGatewayConfiguration.ExternalRestServiceSettings service = configuration.getProgrammableApi()
                .getExternalRestServices()
                .stream()
                .filter(item -> item.getId().equals(serviceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Внешний REST service не найден: " + serviceId));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(service.getBaseUrl() + path))
                .timeout(Duration.ofSeconds(20));

        Map<String, String> mergedHeaders = new HashMap<>();
        if (service.getDefaultHeaders() != null) {
            mergedHeaders.putAll(service.getDefaultHeaders());
        }
        if (headers != null) {
            mergedHeaders.putAll(headers);
        }
        mergedHeaders = exchangeProcessor.enrichHeaders(mergedHeaders, ProgrammableHttpExchangeProcessor.DIRECTION_OUTBOUND_EXTERNAL);
        mergedHeaders.forEach(builder::header);

        String normalizedMethod = method == null ? "GET" : method.toUpperCase();
        Map<String, Object> enrichedBody = exchangeProcessor.enrichBody(body, ProgrammableHttpExchangeProcessor.DIRECTION_OUTBOUND_EXTERNAL);
        String jsonBody = enrichedBody == null || enrichedBody.isEmpty() ? "" : writeBody(enrichedBody);
        HttpRequest request = switch (normalizedMethod) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
            case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody)).build();
            case "DELETE" -> builder.DELETE().build();
            default -> builder.GET().build();
        };

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> processed = new HashMap<>(exchangeProcessor.processResponse(response));
            processed.put("serviceId", serviceId);
            return Map.copyOf(processed);
        } catch (Exception ex) {
            throw new IllegalStateException("Ошибка вызова внешнего REST сервиса", ex);
        }
    }

    private String writeBody(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalArgumentException("body не сериализуется в JSON", ex);
        }
    }
}
