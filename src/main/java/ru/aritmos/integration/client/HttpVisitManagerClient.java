package ru.aritmos.integration.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.domain.BranchStateUpdateRequest;
import ru.aritmos.integration.domain.CallVisitorRequest;
import ru.aritmos.integration.domain.CallVisitorResponse;
import ru.aritmos.integration.domain.QueueItemDto;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * HTTP-клиент VisitManager для интеграции с реальным downstream API.
 */
@Singleton
@Named("rawVisitManagerClient")
@Requires(property = "integration.visit-manager-client.mode", notEquals = "STUB")
public class HttpVisitManagerClient implements VisitManagerClient {

    private final IntegrationGatewayConfiguration configuration;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpVisitManagerClient(IntegrationGatewayConfiguration configuration, ObjectMapper objectMapper) {
        this(configuration, objectMapper, HttpClient.newBuilder().build());
    }

    HttpVisitManagerClient(IntegrationGatewayConfiguration configuration,
                           ObjectMapper objectMapper,
                           HttpClient httpClient) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public List<QueueItemDto> getQueues(String targetVisitManagerId, String branchId) {
        String path = resolvePath(
                configuration.getVisitManagerClient().getQueuesPathTemplate(),
                "/api/v1/queues/{branchId}",
                branchId,
                null
        );
        String body = send(targetVisitManagerId, HttpRequest.newBuilder()
                .uri(uri(targetVisitManagerId, path))
                .GET()
                .build());
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode itemsNode = root.isArray() ? root : root.path("items");
            return objectMapper.convertValue(itemsNode, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Некорректный ответ VisitManager queues API", ex);
        }
    }

    @Override
    public CallVisitorResponse callVisitor(String targetVisitManagerId, String visitorId, CallVisitorRequest request) {
        String requestBody = writeJson(request);
        String path = resolvePath(
                configuration.getVisitManagerClient().getCallPathTemplate(),
                "/api/v1/queues/{branchId}/call/{visitorId}",
                request.branchId(),
                visitorId
        );
        String body = send(targetVisitManagerId, HttpRequest.newBuilder()
                .uri(uri(targetVisitManagerId, path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build());
        try {
            CallVisitorResponse response = objectMapper.readValue(body, CallVisitorResponse.class);
            if (response.sourceVisitManagerId() == null || response.sourceVisitManagerId().isBlank()) {
                return new CallVisitorResponse(response.visitId(), response.status(), targetVisitManagerId);
            }
            return response;
        } catch (Exception ex) {
            throw new IllegalStateException("Некорректный ответ VisitManager call API", ex);
        }
    }

    @Override
    public BranchStateDto getBranchState(String targetVisitManagerId, String branchId) {
        String path = resolvePath(
                configuration.getVisitManagerClient().getBranchStatePathTemplate(),
                "/api/v1/branches/{branchId}/state",
                branchId,
                null
        );
        String body = send(targetVisitManagerId, HttpRequest.newBuilder()
                .uri(uri(targetVisitManagerId, path))
                .GET()
                .build());
        try {
            BranchStateDto state = readBranchState(body);
            return normalize(targetVisitManagerId, branchId, state);
        } catch (Exception ex) {
            throw new IllegalStateException("Некорректный ответ VisitManager branch-state API", ex);
        }
    }

    @Override
    public BranchStateDto updateBranchState(String targetVisitManagerId, String branchId, BranchStateUpdateRequest request) {
        String requestBody = writeJson(request);
        String path = resolvePath(
                configuration.getVisitManagerClient().getBranchStatePathTemplate(),
                "/api/v1/branches/{branchId}/state",
                branchId,
                null
        );
        String body = send(targetVisitManagerId, HttpRequest.newBuilder()
                .uri(uri(targetVisitManagerId, path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                .build());
        try {
            BranchStateDto state = readBranchState(body);
            return normalize(targetVisitManagerId, branchId, state);
        } catch (Exception ex) {
            throw new IllegalStateException("Некорректный ответ VisitManager update-branch-state API", ex);
        }
    }

    private BranchStateDto readBranchState(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        IntegrationGatewayConfiguration.BranchStateResponseMappingSettings mapping =
                configuration.getVisitManagerClient().getBranchStateResponseMapping();
        Instant updatedAt = null;
        JsonNode updatedAtNode = byPath(root, safePath(mapping.getUpdatedAtPath(), "updatedAt"));
        if (updatedAtNode != null && !updatedAtNode.isNull()) {
            updatedAt = Instant.parse(updatedAtNode.asText());
        }
        return new BranchStateDto(
                textByPath(root, safePath(mapping.getBranchIdPath(), "branchId")),
                textByPath(root, safePath(mapping.getSourceVisitManagerIdPath(), "sourceVisitManagerId")),
                textByPath(root, safePath(mapping.getStatusPath(), "status")),
                textByPath(root, safePath(mapping.getActiveWindowPath(), "activeWindow")),
                intByPath(root, safePath(mapping.getQueueSizePath(), "queueSize")),
                updatedAt,
                root.path("cached").asBoolean(false),
                textByPath(root, safePath(mapping.getUpdatedByPath(), "updatedBy"))
        );
    }

    private URI uri(String targetVisitManagerId, String path) {
        IntegrationGatewayConfiguration.VisitManagerInstance instance = configuration.getVisitManagers().stream()
                .filter(item -> targetVisitManagerId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Не найден VisitManager target=" + targetVisitManagerId));
        String baseUrl = instance.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Не задан base-url для VisitManager target=" + targetVisitManagerId);
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBase + path);
    }

    private String send(String targetVisitManagerId, HttpRequest request) {
        IntegrationGatewayConfiguration.VisitManagerClientSettings settings = configuration.getVisitManagerClient();
        HttpRequest.Builder preparedRequest = HttpRequest.newBuilder(request.uri())
                .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
                .timeout(Duration.ofMillis(Math.max(1, settings.getReadTimeoutMillis())));
        request.headers().map().forEach((key, values) -> values.forEach(value -> preparedRequest.header(key, value)));
        if (settings.getAuthToken() != null && !settings.getAuthToken().isBlank()) {
            String authHeader = settings.getAuthHeader() == null || settings.getAuthHeader().isBlank()
                    ? "Authorization"
                    : settings.getAuthHeader();
            preparedRequest.header(authHeader, settings.getAuthToken());
        }
        try {
            HttpResponse<String> response = httpClient.send(preparedRequest.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("VisitManager target=" + targetVisitManagerId
                        + " вернул HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException ex) {
            throw new IllegalStateException("Ошибка HTTP-вызова VisitManager target=" + targetVisitManagerId, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP-вызов VisitManager прерван target=" + targetVisitManagerId, ex);
        }
    }

    private BranchStateDto normalize(String targetVisitManagerId, String branchId, BranchStateDto state) {
        if (state.branchId() == null || state.branchId().isBlank()) {
            throw new IllegalStateException("Некорректный ответ VisitManager branch-state API: отсутствует branchId");
        }
        if (state.sourceVisitManagerId() == null || state.sourceVisitManagerId().isBlank()) {
            throw new IllegalStateException("Некорректный ответ VisitManager branch-state API: отсутствует sourceVisitManagerId");
        }
        if (state.updatedAt() == null) {
            throw new IllegalStateException("Некорректный ответ VisitManager branch-state API: отсутствует updatedAt");
        }
        return new BranchStateDto(
                state.branchId(),
                state.sourceVisitManagerId(),
                state.status(),
                state.activeWindow(),
                state.queueSize(),
                state.updatedAt(),
                false,
                state.updatedBy()
        );
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось сериализовать payload", ex);
        }
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolvePath(String template, String fallbackTemplate, String branchId, String visitorId) {
        String resolved = template == null || template.isBlank()
                ? fallbackTemplate
                : template;
        resolved = resolved.replace("{branchId}", encodePath(branchId));
        resolved = resolved.replace("{visitorId}", visitorId == null ? "" : encodePath(visitorId));
        return resolved.startsWith("/") ? resolved : "/" + resolved;
    }

    private String textByPath(JsonNode node, String path) {
        JsonNode child = byPath(node, path);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }

    private int intByPath(JsonNode node, String path) {
        JsonNode child = byPath(node, path);
        return child == null || child.isNull() ? 0 : child.asInt(0);
    }

    private JsonNode byPath(JsonNode node, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        JsonNode current = node;
        for (String token : path.split("\\.")) {
            if (current == null || current.isNull()) {
                return null;
            }
            current = current.path(token);
        }
        return current == null || current.isMissingNode() ? null : current;
    }

    private String safePath(String configuredPath, String fallbackPath) {
        return configuredPath == null || configuredPath.isBlank() ? fallbackPath : configuredPath;
    }
}
