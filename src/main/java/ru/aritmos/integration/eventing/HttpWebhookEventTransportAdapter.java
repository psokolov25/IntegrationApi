package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Транспортный адаптер отправки событий во внешний webhook (HTTP).
 */
@Singleton
public class HttpWebhookEventTransportAdapter implements EventTransportAdapter {

    private final IntegrationGatewayConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final ExternalSystemAudienceResolver audienceResolver;

    public HttpWebhookEventTransportAdapter(IntegrationGatewayConfiguration configuration,
                                            ObjectMapper objectMapper,
                                            ExternalSystemAudienceResolver audienceResolver) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
        this.audienceResolver = audienceResolver;
    }

    @Override
    public void publish(IntegrationEvent event) {
        IntegrationGatewayConfiguration.EventWebhookSettings webhook = configuration.getEventing().getWebhook();
        if (!webhook.isEnabled()) {
            return;
        }
        Set<String> resolvedTargets = audienceResolver.resolve(event);
        if (!shouldPublishToWebhook(resolvedTargets, webhook.getTargetSystems())) {
            return;
        }
        if (webhook.getUrl() == null || webhook.getUrl().isBlank()) {
            throw new IllegalStateException("eventing.webhook.url обязателен при enabled=true");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(200, webhook.getConnectTimeoutMillis())))
                .build();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(webhook.getUrl()))
                .timeout(Duration.ofMillis(Math.max(200, webhook.getReadTimeoutMillis())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(event, resolvedTargets)));

        if (webhook.getHeaders() != null) {
            webhook.getHeaders().forEach((name, value) -> {
                if (name != null && !name.isBlank() && value != null) {
                    requestBuilder.header(name, value);
                }
            });
        }
        if (webhook.getAuthToken() != null && !webhook.getAuthToken().isBlank()) {
            String authHeader = webhook.getAuthHeader() == null || webhook.getAuthHeader().isBlank()
                    ? "Authorization"
                    : webhook.getAuthHeader();
            requestBuilder.header(authHeader, webhook.getAuthToken());
        }

        HttpRequest request = requestBuilder.build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("webhook transport status=" + response.statusCode());
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Ошибка отправки webhook transport: " + ex.getMessage(), ex);
        }
    }

    private boolean shouldPublishToWebhook(Set<String> resolvedTargets, List<String> configuredTargets) {
        if (configuredTargets == null || configuredTargets.isEmpty()) {
            return true;
        }
        Set<String> targetFilter = configuredTargets.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        if (targetFilter.isEmpty()) {
            return true;
        }
        return resolvedTargets.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(targetFilter::contains);
    }

    private String toJson(IntegrationEvent event, Set<String> resolvedTargets) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("source", event.source());
        body.put("occurredAt", event.occurredAt());
        body.put("targets", resolvedTargets);
        body.put("payload", event.payload());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сериализовать событие для webhook", ex);
        }
    }
}
