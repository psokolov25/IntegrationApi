package ru.aritmos.integration.api;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.HealthStatusResponse;
import ru.aritmos.integration.eventing.EventDispatcherService;
import ru.aritmos.integration.eventing.EventingHealth;

import java.time.Instant;
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
        String status = "UP".equals(eventingStatus) || "DISABLED".equals(eventingStatus) ? "UP" : "DEGRADED";
        return new HealthStatusResponse(
                status,
                "integration-api",
                Instant.now(),
                Map.of(
                        "security-mode", configuration.getSecurityMode().name(),
                        "eventing", eventingStatus
                )
        );
    }

    @Get
    @Operation(summary = "Сводный health", description = "Синоним readiness для упрощённой интеграции monitoring-систем.")
    public HealthStatusResponse health() {
        return readiness();
    }
}
