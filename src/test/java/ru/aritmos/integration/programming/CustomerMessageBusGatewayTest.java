package ru.aritmos.integration.programming;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.util.List;
import java.util.Map;

class CustomerMessageBusGatewayTest {

    @Test
    void shouldPublishViaConfiguredBroker() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.MessageBrokerSettings broker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        broker.setId("customer-databus");
        broker.setType("LOGGING");
        broker.setEnabled(true);
        cfg.getProgrammableApi().setMessageBrokers(List.of(broker));

        CustomerMessageBusGateway gateway = new CustomerMessageBusGateway(cfg, List.of(new LoggingMessageBusAdapter()));
        Map<String, Object> result = gateway.publish(
                "customer-databus",
                "branch.state.changed",
                "BR-1",
                Map.of("branchId", "BR-1", "status", "OPEN"),
                Map.of("x-source", "test")
        );

        Assertions.assertEquals("customer-databus", result.get("brokerId"));
        Assertions.assertEquals("ACCEPTED", result.get("status"));
    }

    @Test
    void shouldFailWhenBrokerMissing() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        CustomerMessageBusGateway gateway = new CustomerMessageBusGateway(cfg, List.of(new LoggingMessageBusAdapter()));
        Assertions.assertThrows(IllegalArgumentException.class, () -> gateway.publish(
                "missing",
                "topic",
                "key",
                Map.of(),
                Map.of()
        ));
    }
}
