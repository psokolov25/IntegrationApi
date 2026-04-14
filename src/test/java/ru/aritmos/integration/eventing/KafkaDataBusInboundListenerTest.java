package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.time.Instant;
import java.util.List;

class KafkaDataBusInboundListenerTest {

    @Test
    void shouldBuildSyntheticInvalidPayloadEventForDlqFlow() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().setEnabled(true);

        EventDispatcherService dispatcherService = new EventDispatcherService(
                configuration,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                new EventOutboxService(),
                event -> {
                },
                List.of(new DefaultVisitCreatedEventHandler())
        );

        KafkaDataBusInboundListener listener = new KafkaDataBusInboundListener(
                configuration,
                dispatcherService,
                new ObjectMapper()
        );

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "integration.inbound",
                2,
                57,
                "key-1",
                "{ invalid-json }"
        );

        IntegrationEvent invalid = listener.toInvalidPayloadEvent(record, new IllegalArgumentException("bad json"));

        Assertions.assertEquals("invalid:integration.inbound:2:57", invalid.eventId());
        Assertions.assertEquals("DATABUS_INVALID_PAYLOAD", invalid.eventType());
        Assertions.assertEquals("kafka-databus:default", invalid.source());
        Assertions.assertTrue(invalid.occurredAt().isBefore(Instant.now().plusSeconds(1)));
        Assertions.assertEquals("default", invalid.payload().get("agentId"));
        Assertions.assertEquals("integration.inbound", invalid.payload().get("topic"));
        Assertions.assertEquals(2, invalid.payload().get("partition"));
        Assertions.assertEquals(57L, invalid.payload().get("offset"));
        Assertions.assertEquals("{ invalid-json }", invalid.payload().get("rawPayloadPreview"));
        Assertions.assertNotNull(invalid.payload().get("rawPayloadHash"));
        Assertions.assertEquals("IllegalArgumentException", invalid.payload().get("error"));
    }

    @Test
    void shouldNormalizeAndTruncateRawPayloadPreviewForInvalidEvent() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().setEnabled(true);

        EventDispatcherService dispatcherService = new EventDispatcherService(
                configuration,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                new EventOutboxService(),
                event -> {
                },
                List.of(new DefaultVisitCreatedEventHandler())
        );

        KafkaDataBusInboundListener listener = new KafkaDataBusInboundListener(
                configuration,
                dispatcherService,
                new ObjectMapper()
        );

        String noisyPayload = "  {   \"secret\":   \"value\"   }  " + "x".repeat(700);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "integration.inbound",
                0,
                1,
                "key-2",
                noisyPayload
        );

        IntegrationEvent invalid = listener.toInvalidPayloadEvent(record, new RuntimeException("parsing failed"));
        String preview = String.valueOf(invalid.payload().get("rawPayloadPreview"));

        Assertions.assertFalse(preview.startsWith(" "));
        Assertions.assertTrue(preview.endsWith("..."));
        Assertions.assertTrue(preview.length() <= 515);
        Assertions.assertEquals(64, String.valueOf(invalid.payload().get("rawPayloadHash")).length());
    }

    @Test
    void shouldResolveMultiAgentKafkaBindingsWithFallbacks() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().setEnabled(true);
        IntegrationGatewayConfiguration.KafkaSettings kafka = configuration.getEventing().getKafka();
        kafka.setBootstrapServers("kafka-global:9092");
        kafka.setConsumerGroup("global-group");
        kafka.setInboundTopic("topic-global");
        kafka.setPollTimeoutMillis(800);

        IntegrationGatewayConfiguration.AgentKafkaSettings central = new IntegrationGatewayConfiguration.AgentKafkaSettings();
        central.setId("agent-central");
        central.setBootstrapServers("kafka-central:9092");
        central.setInboundTopic("topic-central");
        central.setConsumerGroup("group-central");
        central.setPollTimeoutMillis(1200);

        IntegrationGatewayConfiguration.AgentKafkaSettings eastFallback = new IntegrationGatewayConfiguration.AgentKafkaSettings();
        eastFallback.setId("agent-east");
        eastFallback.setInboundTopic("topic-east");

        kafka.setAgents(List.of(central, eastFallback));

        EventDispatcherService dispatcherService = new EventDispatcherService(
                configuration,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                new EventOutboxService(),
                event -> {
                },
                List.of(new DefaultVisitCreatedEventHandler())
        );

        KafkaDataBusInboundListener listener = new KafkaDataBusInboundListener(
                configuration,
                dispatcherService,
                new ObjectMapper()
        );

        List<KafkaDataBusInboundListener.KafkaAgentBinding> bindings = listener.resolveKafkaAgentBindings(kafka);
        Assertions.assertEquals(2, bindings.size());
        Assertions.assertEquals("agent-central", bindings.get(0).agentId());
        Assertions.assertEquals("kafka-central:9092", bindings.get(0).bootstrapServers());
        Assertions.assertEquals("group-central", bindings.get(0).consumerGroup());
        Assertions.assertEquals(1200, bindings.get(0).pollTimeoutMillis());
        Assertions.assertEquals("agent-east", bindings.get(1).agentId());
        Assertions.assertEquals("kafka-global:9092", bindings.get(1).bootstrapServers());
        Assertions.assertEquals("global-group", bindings.get(1).consumerGroup());
        Assertions.assertEquals("topic-east", bindings.get(1).inboundTopic());
        Assertions.assertEquals(800, bindings.get(1).pollTimeoutMillis());
    }

    @Test
    void shouldFilterBindingsByAgentMode() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().setEnabled(true);
        IntegrationGatewayConfiguration.KafkaSettings kafka = configuration.getEventing().getKafka();
        kafka.setBootstrapServers("kafka-global:9092");
        kafka.setInboundTopic("topic-global");

        IntegrationGatewayConfiguration.AgentKafkaSettings central = new IntegrationGatewayConfiguration.AgentKafkaSettings();
        central.setId("agent-central");
        central.setBootstrapServers("kafka-central:9092");
        central.setInboundTopic("topic-central");
        IntegrationGatewayConfiguration.AgentKafkaSettings north = new IntegrationGatewayConfiguration.AgentKafkaSettings();
        north.setId("agent-north");
        north.setBootstrapServers("kafka-north:9092");
        north.setInboundTopic("topic-north");
        kafka.setAgents(List.of(central, north));

        EventDispatcherService dispatcherService = new EventDispatcherService(
                configuration,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                new EventOutboxService(),
                event -> {
                },
                List.of(new DefaultVisitCreatedEventHandler())
        );
        KafkaDataBusInboundListener listener = new KafkaDataBusInboundListener(
                configuration,
                dispatcherService,
                new ObjectMapper()
        );

        kafka.setAgentMode(IntegrationGatewayConfiguration.KafkaAgentMode.ALL_AGENTS);
        Assertions.assertEquals(2, listener.resolveKafkaAgentBindings(kafka).size());

        kafka.setAgentMode(IntegrationGatewayConfiguration.KafkaAgentMode.LOCAL_AGENT);
        kafka.setLocalAgentId("agent-north");
        List<KafkaDataBusInboundListener.KafkaAgentBinding> local = listener.resolveKafkaAgentBindings(kafka);
        Assertions.assertEquals(1, local.size());
        Assertions.assertEquals("agent-north", local.get(0).agentId());

        kafka.setAgentMode(IntegrationGatewayConfiguration.KafkaAgentMode.SELECTED_AGENTS);
        kafka.setSelectedAgentIds(List.of("agent-central"));
        List<KafkaDataBusInboundListener.KafkaAgentBinding> selected = listener.resolveKafkaAgentBindings(kafka);
        Assertions.assertEquals(1, selected.size());
        Assertions.assertEquals("agent-central", selected.get(0).agentId());
    }
}
