package ru.aritmos.integration.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IntegrationGatewayConfigurationTest {

    @Test
    void shouldUseHttpVisitManagerClientModeByDefault() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        Assertions.assertEquals("HTTP", configuration.getVisitManagerClient().getMode());
    }
}
