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
        Assertions.assertEquals("kafka-databus", invalid.source());
        Assertions.assertTrue(invalid.occurredAt().isBefore(Instant.now().plusSeconds(1)));
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
}
