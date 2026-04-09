package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Вызов произвольных REST-операций VisitManager из Groovy-скрипта.
 */
@Singleton
public class VisitManagerRestInvoker {

    private final IntegrationGatewayConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public VisitManagerRestInvoker(IntegrationGatewayConfiguration configuration,
                                   ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> invoke(String targetVisitManagerId,
                                      String method,
                                      String path,
                                      Map<String, Object> body,
                                      Map<String, String> headers) {
        String baseUrl = configuration.getVisitManagers().stream()
                .filter(vm -> vm.getId().equals(targetVisitManagerId))
                .findFirst()
                .map(IntegrationGatewayConfiguration.VisitManagerInstance::getBaseUrl)
                .orElseThrow(() -> new IllegalArgumentException("VisitManager target не найден: " + targetVisitManagerId));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15));

        if (headers != null) {
            headers.forEach(builder::header);
        }

        String normalizedMethod = method == null ? "GET" : method.toUpperCase();
        String bodyJson = body == null ? "" : writeBody(body);
        HttpRequest request = switch (normalizedMethod) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(bodyJson)).build();
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(bodyJson)).build();
            case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(bodyJson)).build();
            case "DELETE" -> builder.DELETE().build();
            default -> builder.GET().build();
        };

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return Map.of(
                    "status", response.statusCode(),
                    "body", response.body(),
                    "headers", response.headers().map()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Ошибка вызова VisitManager REST", ex);
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
