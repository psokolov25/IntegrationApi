package ru.suo.integration.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.suo.integration.config.IntegrationGatewayConfiguration;
import ru.suo.integration.security.ApiKeyAuthService;

import java.util.List;

class ApiKeyAuthServiceTest {

    @Test
    void shouldAllowConfiguredKey() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.setApiKeys(List.of("dev-api-key"));
        ApiKeyAuthService service = new ApiKeyAuthService(cfg);

        Assertions.assertTrue(service.isAllowed("dev-api-key"));
    }

    @Test
    void shouldRejectUnknownKey() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.setApiKeys(List.of("dev-api-key"));
        ApiKeyAuthService service = new ApiKeyAuthService(cfg);

        Assertions.assertFalse(service.isAllowed("wrong"));
        Assertions.assertFalse(service.isAllowed(""));
    }
}
