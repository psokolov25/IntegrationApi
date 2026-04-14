package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class KafkaDataBusInboundMapperTest {

    private final KafkaDataBusInboundMapper mapper = new KafkaDataBusInboundMapper(new ObjectMapper());

    @Test
    void shouldMapCanonicalIntegrationEventPayload() {
        String raw = """
                {
                  "eventId": "evt-101",
                  "eventType": "VISIT_CREATED",
                  "source": "vm-main",
                  "occurredAt": "2026-04-13T10:00:00Z",
                  "payload": {
                    "branchId": "BR-10",
                    "visitManagerId": "vm-main"
                  }
                }
                """;

        IntegrationEvent event = mapper.map(raw, "integration.inbound");

        Assertions.assertEquals("evt-101", event.eventId());
        Assertions.assertEquals("VISIT_CREATED", event.eventType());
        Assertions.assertEquals("vm-main", event.source());
        Assertions.assertEquals("BR-10", event.payload().get("branchId"));
    }

    @Test
    void shouldMapNestedDatabusMetaAndUseDataAsPayload() {
        String raw = """
                {
                  "meta": {
                    "eventId": "evt-202",
                    "eventType": "ENTITY_CHANGED",
                    "source": "databus"
                  },
                  "data": {
                    "branch": {
                      "id": "BR-22"
                    }
                  }
                }
                """;

        IntegrationEvent event = mapper.map(raw, "integration.inbound");

        Assertions.assertEquals("evt-202", event.eventId());
        Assertions.assertEquals("ENTITY_CHANGED", event.eventType());
        Assertions.assertEquals("databus", event.source());
        Assertions.assertTrue(event.payload().containsKey("branch"));
    }

    @Test
    void shouldGenerateDeterministicEventIdFromKafkaMetadataWhenMissing() {
        String raw = """
                {
                  "eventType": "VISIT_UPDATED",
                  "data": {
                    "visitId": "V-1"
                  }
                }
                """;

        IntegrationEvent event = mapper.map(raw, "integration.inbound", 2, 57);

        Assertions.assertEquals("integration.inbound:2:57", event.eventId());
        Assertions.assertEquals("VISIT_UPDATED", event.eventType());
    }
}
