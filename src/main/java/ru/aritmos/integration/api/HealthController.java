package ru.aritmos.integration.api;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.HealthStatusResponse;
import ru.aritmos.integration.eventing.EventDispatcherService;
import ru.aritmos.integration.eventing.EventingHealth;
import ru.aritmos.integration.security.core.SecurityMode;
import ru.aritmos.integration.service.RuntimeSafetyLimitService;

import java.time.Instant;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Минимальный health endpoint этапа 1.
 */
@Controller("/health")
@Tag(name = "Health", description = "Проверки живости/готовности и сводный статус")
public class HealthController {

    private final IntegrationGatewayConfiguration configuration;
    private final EventDispatcherService eventDispatcherService;
    private final RuntimeSafetyLimitService runtimeSafetyLimitService;

    public HealthController(IntegrationGatewayConfiguration configuration,
                            EventDispatcherService eventDispatcherService,
                            RuntimeSafetyLimitService runtimeSafetyLimitService) {
        this.configuration = configuration;
        this.eventDispatcherService = eventDispatcherService;
        this.runtimeSafetyLimitService = runtimeSafetyLimitService;
    }

    @Get("/liveness")
    @Operation(summary = "Liveness", description = "Проверка, что процесс приложения запущен.")
    public HealthStatusResponse liveness() {
        return new HealthStatusResponse(
                "UP",
                "integration-api",
                Instant.now(),
                Map.of("runtime", "UP")
        );
    }

    @Get("/readiness")
    @Operation(summary = "Readiness", description = "Проверка готовности с учётом состояния eventing и конфигурации безопасности.")
    public HealthStatusResponse readiness() {
        String eventingStatus = "DISABLED";
        if (configuration.getEventing().isEnabled()) {
            EventingHealth health = eventDispatcherService.health();
            eventingStatus = health.status();
        }
        Map<String, String> components = new LinkedHashMap<>();
        components.put("security-mode", configuration.getSecurityMode().name());
        components.put("security", resolveSecurityStatus());
        components.put("eventing", eventingStatus);
        components.put("visit-manager-client", resolveVisitManagerClientStatus());
        components.put("gateway", resolveGatewayStatus());
        components.put("federation", resolveFederationStatus());
        components.put("aggregation", resolveAggregationStatus());
        components.put("programmable-api", configuration.getProgrammableApi().isEnabled() ? "ENABLED" : "DISABLED");
        components.put("client-policy", resolveClientPolicyStatus());
        components.put("runtime-safety", runtimeSafetyLimitService.readinessStatus());
        components.put("observability", "UP");

        String status = resolveOverallStatus(components.values());
        return new HealthStatusResponse(
                status,
                "integration-api",
                Instant.now(),
                Map.copyOf(components)
        );
    }

    @Get
    @Operation(summary = "Сводный health", description = "Синоним readiness для упрощённой интеграции monitoring-систем.")
    public HealthStatusResponse health() {
        return readiness();
    }

    private String resolveGatewayStatus() {
        long active = configuration.getVisitManagers().stream()
                .filter(IntegrationGatewayConfiguration.VisitManagerInstance::isActive)
                .count();
        if (active == 0) {
            return "DEGRADED";
        }
        boolean allActiveConfigured = configuration.getVisitManagers().stream()
                .filter(IntegrationGatewayConfiguration.VisitManagerInstance::isActive)
                .allMatch(this::isVisitManagerEndpointConfigured);
        return allActiveConfigured ? "UP" : "DOWN";
    }

    private String resolveFederationStatus() {
        return configuration.getVisitManagers().size() > 1 ? "ENABLED" : "DISABLED";
    }

    private String resolveVisitManagerClientStatus() {
        IntegrationGatewayConfiguration.VisitManagerClientSettings settings = configuration.getVisitManagerClient();
        String mode = settings.getMode() == null ? "HTTP" : settings.getMode().trim().toUpperCase();
        if ("STUB".equals(mode)) {
            return "DOWN";
        }
        if (!"HTTP".equals(mode)) {
            return "DOWN";
        }
        boolean hasActive = configuration.getVisitManagers().stream()
                .anyMatch(IntegrationGatewayConfiguration.VisitManagerInstance::isActive);
        if (!hasActive) {
            return "DEGRADED";
        }
        boolean allActiveConfigured = configuration.getVisitManagers().stream()
                .filter(IntegrationGatewayConfiguration.VisitManagerInstance::isActive)
                .allMatch(this::isVisitManagerEndpointConfigured);
        if (!allActiveConfigured) {
            return "DOWN";
        }
        boolean templatesValid = hasPlaceholder(settings.getQueuesPathTemplate(), "{branchId}")
                && hasPlaceholder(settings.getCallPathTemplate(), "{branchId}")
                && hasPlaceholder(settings.getCallPathTemplate(), "{visitorId}")
                && hasPlaceholder(settings.getBranchStatePathTemplate(), "{branchId}");
        if (!templatesValid) {
            return "DOWN";
        }
        if (settings.isReadinessProbeEnabled() && !probeVisitManagers(settings)) {
            return "DEGRADED";
        }
        return "UP";
    }


    private String resolveAggregationStatus() {
        if (configuration.getAggregateMaxBranches() <= 0) {
            return "DOWN";
        }
        if (configuration.getAggregateRequestTimeoutMillis() <= 0) {
            return "DOWN";
        }
        return "UP";
    }

    private String resolveClientPolicyStatus() {
        IntegrationGatewayConfiguration.ClientPolicySettings policy = configuration.getClientPolicy();
        if (policy.getRetryAttempts() <= 0 && policy.getTimeoutMillis() <= 0) {
            return "DISABLED";
        }
        return "ENABLED";
    }

    private String resolveSecurityStatus() {
        SecurityMode mode = configuration.getSecurityMode();
        return switch (mode) {
            case API_KEY -> configuration.getApiKeys().isEmpty() ? "DOWN" : "UP";
            case INTERNAL -> {
                boolean hasSigningKey = configuration.getInternalSigningKey() != null
                        && !configuration.getInternalSigningKey().isBlank();
                boolean hasClients = !configuration.getInternalClients().isEmpty();
                if (!hasSigningKey) {
                    yield "DOWN";
                }
                yield hasClients ? "UP" : "DEGRADED";
            }
            case KEYCLOAK -> {
                boolean hasIssuer = configuration.getKeycloak().getIssuer() != null
                        && !configuration.getKeycloak().getIssuer().isBlank();
                boolean hasAudience = configuration.getKeycloak().getAudience() != null
                        && !configuration.getKeycloak().getAudience().isBlank();
                if (!hasIssuer || !hasAudience) {
                    yield "DOWN";
                }
                yield "UP";
            }
            case HYBRID -> {
                boolean hasApiKeys = !configuration.getApiKeys().isEmpty();
                boolean hasSigningKey = configuration.getInternalSigningKey() != null
                        && !configuration.getInternalSigningKey().isBlank();
                boolean hasIssuer = configuration.getKeycloak().getIssuer() != null
                        && !configuration.getKeycloak().getIssuer().isBlank();
                if (!hasSigningKey) {
                    yield "DOWN";
                }
                if (!hasApiKeys && !hasIssuer) {
                    yield "DEGRADED";
                }
                yield "UP";
            }
        };
    }

    private String resolveOverallStatus(Collection<String> componentStates) {
        return componentStates.stream()
                .anyMatch(state -> "DOWN".equalsIgnoreCase(state) || "DEGRADED".equalsIgnoreCase(state))
                ? "DEGRADED"
                : "UP";
    }

    private boolean hasPlaceholder(String template, String placeholder) {
        return template != null && template.contains(placeholder);
    }

    private boolean isVisitManagerEndpointConfigured(IntegrationGatewayConfiguration.VisitManagerInstance vm) {
        return vm.getId() != null && !vm.getId().isBlank()
                && vm.getBaseUrl() != null && !vm.getBaseUrl().isBlank();
    }

    private boolean probeVisitManagers(IntegrationGatewayConfiguration.VisitManagerClientSettings settings) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, settings.getReadTimeoutMillis())))
                .build();
        String probePath = normalizeProbePath(settings.getReadinessProbePath());
        return configuration.getVisitManagers().stream()
                .filter(IntegrationGatewayConfiguration.VisitManagerInstance::isActive)
                .allMatch(vm -> probeSingleVisitManager(client, vm, probePath, settings));
    }

    private boolean probeSingleVisitManager(HttpClient client,
                                            IntegrationGatewayConfiguration.VisitManagerInstance vm,
                                            String probePath,
                                            IntegrationGatewayConfiguration.VisitManagerClientSettings settings) {
        try {
            String baseUrl = vm.getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                return false;
            }
            String trimmedBase = baseUrl.trim();
            String normalizedBase = trimmedBase.endsWith("/")
                    ? trimmedBase.substring(0, trimmedBase.length() - 1)
                    : trimmedBase;
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedBase + probePath))
                    .timeout(Duration.ofMillis(Math.max(1, settings.getReadTimeoutMillis())))
                    .GET();
            applyProbeAuthorization(requestBuilder, settings);
            HttpResponse<Void> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizeProbePath(String path) {
        if (path == null || path.isBlank()) {
            return "/health/readiness";
        }
        String normalized = path.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private void applyProbeAuthorization(HttpRequest.Builder requestBuilder,
                                         IntegrationGatewayConfiguration.VisitManagerClientSettings settings) {
        if (settings.getAuthToken() == null || settings.getAuthToken().isBlank()) {
            return;
        }
        String authHeader = settings.getAuthHeader() == null || settings.getAuthHeader().isBlank()
                ? "Authorization"
                : settings.getAuthHeader();
        requestBuilder.header(authHeader, settings.getAuthToken());
    }
}
