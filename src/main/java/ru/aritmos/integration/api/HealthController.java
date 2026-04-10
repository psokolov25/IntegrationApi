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

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Минимальный health endpoint этапа 1.
 */
@Controller("/health")
@Tag(name = "Health", description = "Проверки живости/готовности и сводный статус")
public class HealthController {

    private final IntegrationGatewayConfiguration configuration;
    private final EventDispatcherService eventDispatcherService;

    public HealthController(IntegrationGatewayConfiguration configuration,
                            EventDispatcherService eventDispatcherService) {
        this.configuration = configuration;
        this.eventDispatcherService = eventDispatcherService;
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
        components.put("gateway", resolveGatewayStatus());
        components.put("federation", resolveFederationStatus());
        components.put("aggregation", resolveAggregationStatus());
        components.put("programmable-api", configuration.getProgrammableApi().isEnabled() ? "ENABLED" : "DISABLED");
        components.put("client-policy", resolveClientPolicyStatus());
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
        return active > 0 ? "UP" : "DEGRADED";
    }

    private String resolveFederationStatus() {
        return configuration.getVisitManagers().size() > 1 ? "ENABLED" : "DISABLED";
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
}
