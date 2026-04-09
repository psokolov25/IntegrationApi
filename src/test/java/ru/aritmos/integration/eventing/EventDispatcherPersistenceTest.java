package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class EventDispatcherPersistenceTest {

    @Test
    void shouldRestoreProcessedAndOutboxStateAfterRestart() throws Exception {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setStatePersistenceEnabled(true);
        cfg.getEventing().setStatePersistencePath(Files.createTempDirectory("eventing-persist").resolve("snapshot.json").toString());

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        EventingStatePersistenceService persistence = new EventingStatePersistenceService(cfg, mapper);

        EventDispatcherService first = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                new EventOutboxService(),
                event -> {
                },
                List.of(new DefaultVisitCreatedEventHandler()),
                persistence
        );

        IntegrationEvent event = new IntegrationEvent("persist-1", "visit-created", "databus", Instant.now(), Map.of("x", 1));
        EventProcessingResult result = first.process(event);
        Assertions.assertEquals("PROCESSED", result.status());

        EventDispatcherService restarted = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                new EventOutboxService(),
                eventPayload -> {
                },
                List.of(new DefaultVisitCreatedEventHandler()),
                persistence
        );

        Assertions.assertNotNull(restarted.processedEvent("persist-1"));
        Assertions.assertTrue(restarted.stats().processedCount() >= 1);
    }
}
