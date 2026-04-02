package ru.suo.integration.service;

import jakarta.inject.Singleton;
import ru.suo.integration.config.IntegrationGatewayConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис маршрутизации запросов к конкретной инсталляции VisitManager.
 */
@Singleton
public class RoutingService {

    private final IntegrationGatewayConfiguration configuration;

    public RoutingService(IntegrationGatewayConfiguration configuration) {
        this.configuration = configuration;
    }

    public String resolveTarget(String branchId, String explicitTarget) {
        if (explicitTarget != null && !explicitTarget.isBlank()) {
            return explicitTarget;
        }
        String mapped = configuration.getBranchRouting().get(branchId);
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        return firstActiveTarget();
    }

    public String resolveFallbackTarget(String branchId, String alreadyUsedTarget) {
        String configuredFallback = configuration.getBranchFallbackRouting().get(branchId);
        if (configuredFallback != null && !configuredFallback.isBlank() && !configuredFallback.equals(alreadyUsedTarget)) {
            return configuredFallback;
        }
        return null;
    }

    public Map<String, String> resolveTargetsByBranch(List<String> branchIds) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String branchId : branchIds) {
            result.put(branchId, resolveTarget(branchId, ""));
        }
        return result;
    }

    private String firstActiveTarget() {
        return configuration.getVisitManagers().stream()
                .filter(IntegrationGatewayConfiguration.VisitManagerInstance::isActive)
                .findFirst()
                .map(IntegrationGatewayConfiguration.VisitManagerInstance::getId)
                .orElseThrow(() -> new IllegalStateException("Нет активной инсталляции VisitManager для маршрутизации"));
    }
}
