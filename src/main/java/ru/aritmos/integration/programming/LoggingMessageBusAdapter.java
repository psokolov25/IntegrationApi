package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.time.Instant;
import java.util.Map;

/**
 * Базовый универсальный адаптер шины сообщений (logging placeholder).
 */
@Singleton
public class LoggingMessageBusAdapter implements CustomerMessageBusAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingMessageBusAdapter.class);

    @Override
    public boolean supports(String brokerType) {
        if (brokerType == null || brokerType.isBlank()) {
            return true;
        }
        String normalized = brokerType.toUpperCase();
        return normalized.equals("LOGGING")
                || normalized.equals("KAFKA")
                || normalized.equals("DATABUS")
                || normalized.equals("RABBITMQ")
                || normalized.equals("NATS");
    }

    @Override
    public Map<String, Object> publish(IntegrationGatewayConfiguration.MessageBrokerSettings broker,
                                       BrokerMessageRequest message) {
        LOG.info("CUSTOM_BUS_MESSAGE brokerId={} brokerType={} topic={} key={} headersCount={} payloadFields={}",
                broker.getId(),
                broker.getType(),
                message.topic(),
                message.key(),
                message.headers() == null ? 0 : message.headers().size(),
                message.payload() == null ? 0 : message.payload().size());
        return Map.of(
                "brokerId", broker.getId(),
                "brokerType", broker.getType(),
                "topic", message.topic(),
                "status", "ACCEPTED",
                "timestamp", Instant.now().toString()
        );
    }
}
