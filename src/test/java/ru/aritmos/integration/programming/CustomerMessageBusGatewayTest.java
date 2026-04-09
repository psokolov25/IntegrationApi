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

    @Test
    void shouldSupportExtendedBrokerTypeCatalog() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.MessageBrokerSettings broker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        broker.setId("redis-stream-bus");
        broker.setType("REDIS_STREAM");
        broker.setEnabled(true);
        cfg.getProgrammableApi().setMessageBrokers(List.of(broker));

        CustomerMessageBusGateway gateway = new CustomerMessageBusGateway(cfg, List.of(new LoggingMessageBusAdapter()));
        Map<String, Object> result = gateway.publish(
                "redis-stream-bus",
                "customer.events",
                "evt-1",
                Map.of("hello", "world"),
                Map.of()
        );
        Assertions.assertEquals("ACCEPTED", result.get("status"));
    }

    @Test
    void shouldSupportSolaceBrokerType() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.MessageBrokerSettings broker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        broker.setId("solace-bus");
        broker.setType("SOLACE");
        broker.setEnabled(true);
        cfg.getProgrammableApi().setMessageBrokers(List.of(broker));

        CustomerMessageBusGateway gateway = new CustomerMessageBusGateway(cfg, List.of(new LoggingMessageBusAdapter()));
        Map<String, Object> result = gateway.publish(
                "solace-bus",
                "customer.events",
                "evt-2",
                Map.of("hello", "solace"),
                Map.of()
        );
        Assertions.assertEquals("ACCEPTED", result.get("status"));
    }

    @Test
    void shouldSupportAwsKinesisBrokerType() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.MessageBrokerSettings broker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        broker.setId("kinesis-bus");
        broker.setType("AWS_KINESIS");
        broker.setEnabled(true);
        cfg.getProgrammableApi().setMessageBrokers(List.of(broker));

        CustomerMessageBusGateway gateway = new CustomerMessageBusGateway(cfg, List.of(new LoggingMessageBusAdapter()));
        Map<String, Object> result = gateway.publish(
                "kinesis-bus",
                "customer.events",
                "evt-3",
                Map.of("hello", "kinesis"),
                Map.of()
        );
        Assertions.assertEquals("ACCEPTED", result.get("status"));
    }
}
