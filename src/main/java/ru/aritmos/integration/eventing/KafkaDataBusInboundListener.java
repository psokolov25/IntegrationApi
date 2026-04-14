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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
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
    private final List<ListenerWorker> workers = new ArrayList<>();

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
        List<KafkaAgentBinding> bindings = resolveKafkaAgentBindings(kafka);
        if (bindings.isEmpty()) {
            LOG.warn("KAFKA_DATABUS_LISTENER_DISABLED reason=no-active-agents");
            return;
        }

        for (KafkaAgentBinding binding : bindings) {
            if (binding.bootstrapServers().isBlank()) {
                LOG.warn("KAFKA_DATABUS_AGENT_DISABLED agentId={} reason=bootstrapServers-empty", binding.agentId());
                continue;
            }
            if (binding.inboundTopic().isBlank()) {
                LOG.warn("KAFKA_DATABUS_AGENT_DISABLED agentId={} reason=inboundTopic-empty", binding.agentId());
                continue;
            }
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, binding.bootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, binding.consumerGroup());
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, binding.autoOffsetReset());

            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
            consumer.subscribe(List.of(binding.inboundTopic()));
            ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "kafka-databus-inbound-listener-" + binding.agentId());
                thread.setDaemon(true);
                return thread;
            });
            ListenerWorker worker = new ListenerWorker(binding, consumer, executor);
            workers.add(worker);
            executor.submit(() -> pollLoop(worker));
            LOG.info("KAFKA_DATABUS_AGENT_STARTED agentId={} topic={} groupId={}",
                    binding.agentId(), binding.inboundTopic(), binding.consumerGroup());
        }
        if (workers.isEmpty()) {
            LOG.warn("KAFKA_DATABUS_LISTENER_DISABLED reason=no-valid-agents");
        }
    }

    @PreDestroy
    void stop() {
        for (ListenerWorker worker : workers) {
            worker.running().set(false);
            worker.consumer().wakeup();
        }
        for (ListenerWorker worker : workers) {
            worker.executor().shutdown();
            try {
                if (!worker.executor().awaitTermination(2, TimeUnit.SECONDS)) {
                    worker.executor().shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                worker.executor().shutdownNow();
            }
            try {
                worker.consumer().close(Duration.ofSeconds(1));
            } catch (Exception ignored) {
                // noop
            }
        }
        workers.clear();
    }

    private void pollLoop(ListenerWorker worker) {
        Duration timeout = Duration.ofMillis(Math.max(100, worker.binding().pollTimeoutMillis()));
        while (worker.running().get()) {
            try {
                ConsumerRecords<String, String> records = worker.consumer().poll(timeout);
                Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();
                for (ConsumerRecord<String, String> record : records) {
                    boolean shouldCommit = handleRecord(record, worker.binding().agentId());
                    if (shouldCommit) {
                        TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
                        offsetsToCommit.put(topicPartition, new OffsetAndMetadata(record.offset() + 1));
                    }
                }
                commitOffsets(worker, offsetsToCommit);
            } catch (WakeupException ex) {
                if (worker.running().get()) {
                    LOG.warn("KAFKA_DATABUS_LISTENER_WAKEUP_UNEXPECTED agentId={} message={}",
                            worker.binding().agentId(), ex.getMessage());
                }
            } catch (Exception ex) {
                LOG.warn("KAFKA_DATABUS_LISTENER_ERROR agentId={} message={}",
                        worker.binding().agentId(), ex.getMessage());
            }
        }
    }

    private boolean handleRecord(ConsumerRecord<String, String> record, String agentId) {
        try {
            IntegrationEvent event = inboundMapper.map(record.value(), record.topic(), record.partition(), record.offset());
            EventProcessingResult result = dispatcherService.process(event);
            LOG.info("KAFKA_DATABUS_EVENT_PROCESSED agentId={} topic={} partition={} offset={} eventId={} status={}",
                    agentId,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    result.eventId(),
                    result.status());
            return true;
        } catch (Exception ex) {
            return processInvalidPayload(record, ex, agentId);
        }
    }

    private boolean processInvalidPayload(ConsumerRecord<String, String> record, Exception rootCause, String agentId) {
        try {
            IntegrationEvent invalidEvent = toInvalidPayloadEvent(record, rootCause, agentId);
            EventProcessingResult result = dispatcherService.process(invalidEvent);
            LOG.warn("KAFKA_DATABUS_INVALID_PAYLOAD_DLQ agentId={} topic={} partition={} offset={} status={} errorType={}",
                    agentId,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    result.status(),
                    rootCause == null ? "unknown" : rootCause.getClass().getSimpleName());
            return true;
        } catch (Exception ex) {
            LOG.warn("KAFKA_DATABUS_EVENT_FAILED agentId={} topic={} partition={} offset={} errorType={}",
                    agentId,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    ex.getClass().getSimpleName());
            return false;
        }
    }

    IntegrationEvent toInvalidPayloadEvent(ConsumerRecord<String, String> record, Exception rootCause) {
        return toInvalidPayloadEvent(record, rootCause, "default");
    }

    IntegrationEvent toInvalidPayloadEvent(ConsumerRecord<String, String> record, Exception rootCause, String agentId) {
        String rawPayload = record.value() == null ? "" : record.value();
        return new IntegrationEvent(
                "invalid:" + record.topic() + ":" + record.partition() + ":" + record.offset(),
                "DATABUS_INVALID_PAYLOAD",
                "kafka-databus:" + (agentId == null || agentId.isBlank() ? "default" : agentId),
                Instant.now(),
                Map.of(
                        "agentId", agentId == null || agentId.isBlank() ? "default" : agentId,
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

    private void commitOffsets(ListenerWorker worker, Map<TopicPartition, OffsetAndMetadata> offsetsToCommit) {
        if (offsetsToCommit == null || offsetsToCommit.isEmpty()) {
            return;
        }
        try {
            worker.consumer().commitSync(offsetsToCommit);
        } catch (Exception ex) {
            LOG.warn("KAFKA_DATABUS_OFFSET_COMMIT_FAILED agentId={} partitions={} reason={}",
                    worker.binding().agentId(),
                    offsetsToCommit.keySet(),
                    ex.getMessage());
        }
    }

    List<KafkaAgentBinding> resolveKafkaAgentBindings(IntegrationGatewayConfiguration.KafkaSettings kafka) {
        List<IntegrationGatewayConfiguration.AgentKafkaSettings> configuredAgents = kafka.getAgents();
        if (configuredAgents == null || configuredAgents.isEmpty()) {
            return List.of(new KafkaAgentBinding(
                    "default",
                    trimmed(kafka.getBootstrapServers()),
                    defaultIfBlank(kafka.getConsumerGroup(), "integration-api-databus"),
                    defaultIfBlank(kafka.getAutoOffsetReset(), "latest"),
                    Math.max(100, kafka.getPollTimeoutMillis()),
                    trimmed(kafka.getInboundTopic())
            ));
        }
        List<KafkaAgentBinding> bindings = new ArrayList<>();
        for (IntegrationGatewayConfiguration.AgentKafkaSettings agent : configuredAgents) {
            if (agent == null || !agent.isEnabled()) {
                continue;
            }
            bindings.add(new KafkaAgentBinding(
                    defaultIfBlank(agent.getId(), "agent-" + (bindings.size() + 1)),
                    defaultIfBlank(trimmed(agent.getBootstrapServers()), trimmed(kafka.getBootstrapServers())),
                    defaultIfBlank(agent.getConsumerGroup(), defaultIfBlank(kafka.getConsumerGroup(), "integration-api-databus")),
                    defaultIfBlank(agent.getAutoOffsetReset(), defaultIfBlank(kafka.getAutoOffsetReset(), "latest")),
                    agent.getPollTimeoutMillis() > 0 ? agent.getPollTimeoutMillis() : Math.max(100, kafka.getPollTimeoutMillis()),
                    defaultIfBlank(trimmed(agent.getInboundTopic()), trimmed(kafka.getInboundTopic()))
            ));
        }
        return bindings.stream()
                .filter(binding -> !binding.bootstrapServers().isBlank())
                .filter(binding -> !binding.inboundTopic().isBlank())
                .filter(distinctAgentId())
                .filter(agentFilter(kafka))
                .toList();
    }

    private java.util.function.Predicate<KafkaAgentBinding> distinctAgentId() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        return binding -> seen.add(binding.agentId());
    }

    private java.util.function.Predicate<KafkaAgentBinding> agentFilter(IntegrationGatewayConfiguration.KafkaSettings kafka) {
        IntegrationGatewayConfiguration.KafkaAgentMode mode = kafka.getAgentMode() == null
                ? IntegrationGatewayConfiguration.KafkaAgentMode.ALL_AGENTS
                : kafka.getAgentMode();
        String localAgentId = defaultIfBlank(kafka.getLocalAgentId(), "");
        Set<String> selected = new LinkedHashSet<>();
        if (kafka.getSelectedAgentIds() != null) {
            kafka.getSelectedAgentIds().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .forEach(selected::add);
        }
        return switch (mode) {
            case LOCAL_AGENT -> binding -> !localAgentId.isBlank() && localAgentId.equals(binding.agentId());
            case SELECTED_AGENTS -> binding -> selected.contains(binding.agentId());
            default -> binding -> true;
        };
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String trimmed(String value) {
        return Objects.toString(value, "").trim();
    }

    record KafkaAgentBinding(
            String agentId,
            String bootstrapServers,
            String consumerGroup,
            String autoOffsetReset,
            long pollTimeoutMillis,
            String inboundTopic
    ) {
    }

    private record ListenerWorker(
            KafkaAgentBinding binding,
            KafkaConsumer<String, String> consumer,
            ExecutorService executor,
            AtomicBoolean running
    ) {
        private ListenerWorker(KafkaAgentBinding binding, KafkaConsumer<String, String> consumer, ExecutorService executor) {
            this(binding, consumer, executor, new AtomicBoolean(true));
        }
    }
}
