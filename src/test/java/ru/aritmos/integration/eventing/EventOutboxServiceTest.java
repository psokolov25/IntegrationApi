package ru.aritmos.integration.eventing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class EventOutboxServiceTest {

    @Test
    void shouldFilterSnapshotByStatusAndIncludeSent() {
        EventOutboxService outbox = new EventOutboxService();
        outbox.stage(new IntegrationEvent("evt-1", "visit-created", "test", Instant.now(), Map.of()));
        outbox.stage(new IntegrationEvent("evt-2", "visit-created", "test", Instant.now(), Map.of()));
        outbox.markAttempt("evt-2");
        outbox.markSent("evt-2");

        List<EventOutboxMessage> noSent = outbox.snapshot(20, "", false);
        Assertions.assertTrue(noSent.stream().noneMatch(item -> "SENT".equals(item.status())));

        List<EventOutboxMessage> sent = outbox.snapshot(20, "SENT", true);
        Assertions.assertEquals(1, sent.size());
        Assertions.assertEquals("evt-2", sent.get(0).eventId());
    }
}
