package ru.suo.integration.security.internal;

import jakarta.inject.Singleton;
import ru.suo.integration.config.IntegrationGatewayConfiguration;
import ru.suo.integration.security.core.TokenService;

/**
 * Логика выдачи токена для internal-клиентов.
 */
@Singleton
public class InternalAuthApplicationService {

    private final IntegrationGatewayConfiguration configuration;
    private final TokenService tokenService;

    public InternalAuthApplicationService(IntegrationGatewayConfiguration configuration, TokenService tokenService) {
        this.configuration = configuration;
        this.tokenService = tokenService;
    }

    public String issueToken(String clientId, String clientSecret) {
        IntegrationGatewayConfiguration.InternalClient client = configuration.getInternalClients().stream()
                .filter(item -> item.getClientId().equals(clientId))
                .findFirst()
                .orElseThrow(() -> new SecurityException("Неизвестный internal client"));
        if (!client.getClientSecret().equals(clientSecret)) {
            throw new SecurityException("Неверный clientSecret");
        }
        return tokenService.issueToken(clientId, configuration.getInternalSigningKey(), client.getPermissions());
    }
}
