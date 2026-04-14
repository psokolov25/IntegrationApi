package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Inbound listener для подчиненной Kafka/DataBus шины (СУО).
 */
@Singleton
public class KafkaDataBusInboundListener {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaDataBusInboundListener.class);
    private static final int INVALID_PAYLOAD_PREVIEW_MAX_CHARS = 512;

    private final IntegrationGatewayConfiguration configuration;
    private final EventDispatcherService dispatcherService;
    private final KafkaDataBusInboundMapper inboundMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private KafkaConsumer<String, String> consumer;

    public KafkaDataBusInboundListener(IntegrationGatewayConfiguration configuration,
                                       EventDispatcherService dispatcherService,
                                       ObjectMapper objectMapper) {
        this(configuration, dispatcherService, new KafkaDataBusInboundMapper(objectMapper));
    }

    KafkaDataBusInboundListener(IntegrationGatewayConfiguration configuration,
                                EventDispatcherService dispatcherService,
                                KafkaDataBusInboundMapper inboundMapper) {
        this.configuration = configuration;
        this.dispatcherService = dispatcherService;
        this.inboundMapper = inboundMapper;
    }

    @PostConstruct
    void start() {
        IntegrationGatewayConfiguration.KafkaSettings kafka = configuration.getEventing().getKafka();
        if (!configuration.getEventing().isEnabled() || !kafka.isEnabled()) {
            return;
        }
        if (kafka.getBootstrapServers() == null || kafka.getBootstrapServers().isBlank()) {
            LOG.warn("KAFKA_DATABUS_LISTENER_DISABLED reason=bootstrapServers-empty");
            return;
        }
        if (kafka.getInboundTopic() == null || kafka.getInboundTopic().isBlank()) {
            LOG.warn("KAFKA_DATABUS_LISTENER_DISABLED reason=inboundTopic-empty");
            return;
        }

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers().trim());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafka.getConsumerGroup() == null || kafka.getConsumerGroup().isBlank()
                ? "integration-api-databus"
                : kafka.getConsumerGroup().trim());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafka.getAutoOffsetReset() == null || kafka.getAutoOffsetReset().isBlank()
                ? "latest"
                : kafka.getAutoOffsetReset().trim());

        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(List.of(kafka.getInboundTopic().trim()));
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "kafka-databus-inbound-listener");
            thread.setDaemon(true);
            return thread;
        });
        running.set(true);
        this.executor.submit(this::pollLoop);
        LOG.info("KAFKA_DATABUS_LISTENER_STARTED topic={} groupId={}", kafka.getInboundTopic(), props.get("group.id"));
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
        if (consumer != null) {
            try {
                consumer.close(Duration.ofSeconds(1));
            } catch (Exception ignored) {
                // noop
            }
        }
    }

    private void pollLoop() {
        IntegrationGatewayConfiguration.KafkaSettings kafka = configuration.getEventing().getKafka();
        Duration timeout = Duration.ofMillis(Math.max(100, kafka.getPollTimeoutMillis()));
        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(timeout);
                Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();
                for (ConsumerRecord<String, String> record : records) {
                    boolean shouldCommit = handleRecord(record);
                    if (shouldCommit) {
                        TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
                        offsetsToCommit.put(topicPartition, new OffsetAndMetadata(record.offset() + 1));
                    }
                }
                commitOffsets(offsetsToCommit);
            } catch (WakeupException ex) {
                if (running.get()) {
                    LOG.warn("KAFKA_DATABUS_LISTENER_WAKEUP_UNEXPECTED message={}", ex.getMessage());
                }
            } catch (Exception ex) {
                LOG.warn("KAFKA_DATABUS_LISTENER_ERROR message={}", ex.getMessage());
            }
        }
    }

    private boolean handleRecord(ConsumerRecord<String, String> record) {
        try {
            IntegrationEvent event = inboundMapper.map(record.value(), record.topic(), record.partition(), record.offset());
            EventProcessingResult result = dispatcherService.process(event);
            LOG.info("KAFKA_DATABUS_EVENT_PROCESSED topic={} partition={} offset={} eventId={} status={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    result.eventId(),
                    result.status());
            return true;
        } catch (Exception ex) {
            return processInvalidPayload(record, ex);
        }
    }

    private boolean processInvalidPayload(ConsumerRecord<String, String> record, Exception rootCause) {
        try {
            IntegrationEvent invalidEvent = toInvalidPayloadEvent(record, rootCause);
            EventProcessingResult result = dispatcherService.process(invalidEvent);
            LOG.warn("KAFKA_DATABUS_INVALID_PAYLOAD_DLQ topic={} partition={} offset={} status={} errorType={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    result.status(),
                    rootCause == null ? "unknown" : rootCause.getClass().getSimpleName());
            return true;
        } catch (Exception ex) {
            LOG.warn("KAFKA_DATABUS_EVENT_FAILED topic={} partition={} offset={} errorType={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    ex.getClass().getSimpleName());
            return false;
        }
    }

    IntegrationEvent toInvalidPayloadEvent(ConsumerRecord<String, String> record, Exception rootCause) {
        String rawPayload = record.value() == null ? "" : record.value();
        return new IntegrationEvent(
                "invalid:" + record.topic() + ":" + record.partition() + ":" + record.offset(),
                "DATABUS_INVALID_PAYLOAD",
                "kafka-databus",
                Instant.now(),
                Map.of(
                        "topic", record.topic(),
                        "partition", record.partition(),
                        "offset", record.offset(),
                        "rawPayloadPreview", sanitizePayloadPreview(rawPayload),
                        "rawPayloadHash", sha256(rawPayload),
                        "error", rootCause == null ? "unknown" : rootCause.getClass().getSimpleName()
                )
        );
    }

    private String sanitizePayloadPreview(String rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            return "";
        }
        String normalized = rawPayload.replaceAll("\\s+", " ").trim();
        return normalized.length() <= INVALID_PAYLOAD_PREVIEW_MAX_CHARS
                ? normalized
                : normalized.substring(0, INVALID_PAYLOAD_PREVIEW_MAX_CHARS) + "...";
    }

    private String sha256(String rawPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((rawPayload == null ? "" : rawPayload).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            return "n/a";
        }
    }

    private void commitOffsets(Map<TopicPartition, OffsetAndMetadata> offsetsToCommit) {
        if (offsetsToCommit == null || offsetsToCommit.isEmpty()) {
            return;
        }
        try {
            consumer.commitSync(offsetsToCommit);
        } catch (Exception ex) {
            LOG.warn("KAFKA_DATABUS_OFFSET_COMMIT_FAILED partitions={} reason={}",
                    offsetsToCommit.keySet(),
                    ex.getMessage());
        }
    }
}
