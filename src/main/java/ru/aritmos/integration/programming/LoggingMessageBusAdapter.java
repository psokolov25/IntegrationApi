package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Базовый универсальный адаптер шины сообщений (logging placeholder).
 */
@Singleton
public class LoggingMessageBusAdapter implements CustomerMessageBusAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingMessageBusAdapter.class);
    private static final List<String> SUPPORTED_TYPES = List.of(
            "LOGGING", "KAFKA", "DATABUS", "RABBITMQ", "NATS",
            "PULSAR", "ACTIVEMQ", "AZURE_SERVICE_BUS", "AZURE_EVENT_HUB",
            "REDPANDA", "SQS", "MQTT", "AWS_SNS", "IBM_MQ",
            "REDIS_STREAM", "GOOGLE_PUBSUB", "ROCKETMQ",
            "AMQP_1_0", "SOLACE", "APACHE_ARTEMIS",
            "AWS_KINESIS", "NSQ"
    );

    @Override
    public boolean supports(String brokerType) {
        if (brokerType == null || brokerType.isBlank()) {
            return true;
        }
        return SUPPORTED_TYPES.contains(brokerType.toUpperCase());
    }

    @Override
    public List<String> supportedBrokerTypes() {
        return SUPPORTED_TYPES;
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
