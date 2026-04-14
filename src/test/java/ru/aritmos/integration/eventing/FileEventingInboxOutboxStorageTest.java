package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

class FileEventingInboxOutboxStorageTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldPersistInboxAndOutboxToFileAndRestoreAfterRestart() throws Exception {
        Path dir = Files.createTempDirectory("eventing-storage");
        FileEventingInboxOutboxStorage storage = new FileEventingInboxOutboxStorage(dir, objectMapper);

        Instant now = Instant.now();
        storage.saveInbox(Map.of(
                "evt-1",
                new EventInboxService.InboxEntry("evt-1", now, now, 1, "PROCESSED", null)
        ));
        storage.saveOutbox(Map.of(
                "evt-2",
                new EventOutboxMessage(
                        "evt-2",
                        new IntegrationEvent("evt-2", "visit-created", "vm-main", now, Map.of("branchId", "BR-01")),
                        "PENDING",
                        0,
                        null,
                        now,
                        now
                )
        ));

        FileEventingInboxOutboxStorage reloaded = new FileEventingInboxOutboxStorage(dir, objectMapper);
        Map<String, EventInboxService.InboxEntry> inbox = reloaded.loadInbox();
        Map<String, EventOutboxMessage> outbox = reloaded.loadOutbox();

        Assertions.assertEquals(1, inbox.size());
        Assertions.assertEquals("PROCESSED", inbox.get("evt-1").status());
        Assertions.assertEquals(1, outbox.size());
        Assertions.assertEquals("PENDING", outbox.get("evt-2").status());
        Assertions.assertEquals("visit-created", outbox.get("evt-2").event().eventType());
    }

    @Test
    void servicesShouldReuseSameStorageSnapshotAfterRecreation() throws Exception {
        Path dir = Files.createTempDirectory("eventing-storage-services");
        FileEventingInboxOutboxStorage storage = new FileEventingInboxOutboxStorage(dir, objectMapper);
        Instant now = Instant.now();
        IntegrationEvent event = new IntegrationEvent("evt-100", "visit-created", "vm-main", now, Map.of());

        EventInboxService inboxWriter = new EventInboxService(storage);
        EventOutboxService outboxWriter = new EventOutboxService(storage);
        inboxWriter.beginProcessing(event.eventId());
        inboxWriter.markProcessed(event.eventId());
        outboxWriter.stage(event);

        EventInboxService inboxReader = new EventInboxService(storage);
        EventOutboxService outboxReader = new EventOutboxService(storage);

        Assertions.assertTrue(inboxReader.contains("evt-100"));
        Assertions.assertEquals("PROCESSED", inboxReader.snapshot(1, "").get(0).status());
        Assertions.assertNotNull(outboxReader.getById("evt-100"));
        Assertions.assertEquals("PENDING", outboxReader.getById("evt-100").status());
    }
}
