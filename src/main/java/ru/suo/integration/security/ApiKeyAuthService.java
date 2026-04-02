package ru.suo.integration.security;

import jakarta.inject.Singleton;
import ru.suo.integration.config.IntegrationGatewayConfiguration;

/**
 * Простая проверка API-ключа для раннего этапа.
 */
@Singleton
public class ApiKeyAuthService {

    private final IntegrationGatewayConfiguration configuration;

    public ApiKeyAuthService(IntegrationGatewayConfiguration configuration) {
        this.configuration = configuration;
    }

    public boolean isAllowed(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return configuration.getApiKeys().contains(apiKey);
    }
}
