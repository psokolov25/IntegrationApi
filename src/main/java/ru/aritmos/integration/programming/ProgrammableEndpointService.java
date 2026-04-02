package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.security.core.AuthorizationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;
import ru.aritmos.integration.service.GatewayService;

import java.util.Arrays;

/**
 * Безопасный декларативный движок programmable endpoints (без eval).
 */
@Singleton
public class ProgrammableEndpointService {

    private final IntegrationGatewayConfiguration configuration;
    private final GatewayService gatewayService;
    private final AuthorizationService authorizationService;

    public ProgrammableEndpointService(IntegrationGatewayConfiguration configuration,
                                       GatewayService gatewayService,
                                       AuthorizationService authorizationService) {
        this.configuration = configuration;
        this.gatewayService = gatewayService;
        this.authorizationService = authorizationService;
    }

    public Object execute(String endpointId, SubjectPrincipal subject, JsonNode payload) {
        if (!configuration.getProgrammableApi().isEnabled()) {
            throw new IllegalStateException("Programmable API отключен");
        }

        IntegrationGatewayConfiguration.ProgrammableEndpoint endpoint = configuration.getProgrammableApi().getEndpoints().stream()
                .filter(item -> item.getId().equals(endpointId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Endpoint не найден: " + endpointId));

        authorizationService.requirePermission(subject, endpoint.getRequiredPermission());

        ProgrammableOperation operation = ProgrammableOperation.valueOf(endpoint.getOperation());
        return switch (operation) {
            case FETCH_QUEUES -> gatewayService.getQueues(
                    subject.subjectId(),
                    payload.get("branchId").asText(),
                    payload.has("target") ? payload.get("target").asText("") : ""
            );
            case AGGREGATE_QUEUES -> gatewayService.getAggregatedQueues(
                    subject.subjectId(),
                    Arrays.stream(payload.get("branchIds").asText().split(","))
                            .map(String::trim)
                            .filter(v -> !v.isBlank())
                            .toList()
            );
        };
    }
}
