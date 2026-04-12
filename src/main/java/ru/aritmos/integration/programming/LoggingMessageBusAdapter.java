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
    public List<Map<String, Object>> supportedBrokerProfiles() {
        return List.of(
                profile("KAFKA", "Apache Kafka/Redpanda совместимые шины", Map.of("bootstrapServers", "kafka:9092", "acks", "all")),
                profile("RABBITMQ", "RabbitMQ/AMQP 0.9.1", Map.of("host", "rabbitmq", "port", "5672", "exchange", "events")),
                profile("NATS", "NATS / JetStream", Map.of("servers", "nats://localhost:4222", "subjectPrefix", "customer.events")),
                profile("PULSAR", "Apache Pulsar", Map.of("serviceUrl", "pulsar://localhost:6650", "tenant", "public", "namespace", "default")),
                profile("AZURE_SERVICE_BUS", "Azure Service Bus", Map.of("namespace", "customer-bus", "entity", "topic/orders")),
                profile("GOOGLE_PUBSUB", "Google Pub/Sub", Map.of("projectId", "customer-project", "topic", "events")),
                profile("AWS_KINESIS", "AWS Kinesis Data Streams", Map.of("streamName", "customer-events", "region", "eu-central-1")),
                profile("AWS_SNS", "AWS SNS", Map.of("topicArn", "arn:aws:sns:eu-central-1:123456789012:customer-events", "region", "eu-central-1")),
                profile("SQS", "AWS SQS", Map.of("queueUrl", "https://sqs.eu-central-1.amazonaws.com/123456789012/customer-events", "region", "eu-central-1")),
                profile("IBM_MQ", "IBM MQ", Map.of("queueManager", "QM1", "channel", "DEV.APP.SVRCONN", "queue", "EVENTS.Q")),
                profile("SOLACE", "Solace PubSub+", Map.of("host", "tcp://solace:55555", "vpn", "default", "topicPrefix", "events/")),
                profile("MQTT", "MQTT брокеры/IoT", Map.of("brokerUrl", "tcp://mqtt:1883", "qos", "1", "retain", "false")),
                profile("AZURE_EVENT_HUB", "Azure Event Hubs", Map.of("namespace", "customer-hub", "eventHub", "visits", "consumerGroup", "$Default")),
                profile("REDIS_STREAM", "Redis Streams", Map.of("redisUrl", "redis://localhost:6379", "streamKey", "customer.events", "group", "integration")),
                profile("ROCKETMQ", "Apache RocketMQ", Map.of("nameServer", "rocketmq:9876", "producerGroup", "integration-producer")),
                profile("APACHE_ARTEMIS", "Apache ActiveMQ Artemis", Map.of("brokerUrl", "tcp://artemis:61616", "queue", "customer.events")),
                profile("NSQ", "NSQ", Map.of("nsqdTcpAddress", "nsqd:4150", "topic", "customer.events")),
                profile("AMQP_1_0", "AMQP 1.0 брокеры", Map.of("endpoint", "amqp://broker:5672", "address", "customer.events")),
                profile("ACTIVEMQ", "Apache ActiveMQ Classic", Map.of("brokerUrl", "tcp://activemq:61616", "queue", "customer.events"))
        );
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

    private Map<String, Object> profile(String type, String description, Map<String, String> propertyTemplate) {
        List<String> keys = propertyTemplate.keySet().stream().sorted().toList();
        List<String> required = keys.isEmpty() ? List.of() : List.of(keys.get(0));
        List<String> optional = keys.size() <= 1 ? List.of() : keys.subList(1, keys.size());
        return Map.of(
                "type", type,
                "description", description,
                "adapterMode", "LOGGING_PLACEHOLDER",
                "requiredProperties", required,
                "optionalProperties", optional,
                "propertyTemplate", propertyTemplate
        );
    }
}
