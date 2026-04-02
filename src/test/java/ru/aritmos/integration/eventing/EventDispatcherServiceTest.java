package ru.aritmos.integration.eventing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class EventDispatcherServiceTest {

    @Test
    void shouldProcessAndDetectDuplicate() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setMaxRetries(1);

        EventStoreService storeService = new EventStoreService();
        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                storeService,
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        IntegrationEvent event = new IntegrationEvent("e-1", "visit-created", "databus", Instant.now(), Map.of("x", 1));

        EventProcessingResult first = dispatcher.process(event);
        EventProcessingResult second = dispatcher.process(event);

        Assertions.assertEquals("PROCESSED", first.status());
        Assertions.assertEquals("DUPLICATE", second.status());
        Assertions.assertNotNull(storeService.getById("e-1"));
    }

    @Test
    void shouldSendToDlqWhenNoHandler() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setMaxRetries(0);

        EventRetryService retryService = new EventRetryService();
        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                retryService,
                new EventStoreService(),
                event -> {},
                List.of()
        );

        IntegrationEvent event = new IntegrationEvent("e-2", "unknown", "databus", Instant.now(), Map.of());
        EventProcessingResult result = dispatcher.process(event);

        Assertions.assertEquals("DLQ", result.status());
        Assertions.assertEquals(1, retryService.dlqSnapshot().size());
    }

    @Test
    void shouldReplayProcessedEvent() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setMaxRetries(1);

        EventStoreService storeService = new EventStoreService();
        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                storeService,
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        dispatcher.process(new IntegrationEvent("e-3", "visit-created", "databus", Instant.now(), Map.of()));
        EventProcessingResult replay = dispatcher.replay("e-3");

        Assertions.assertEquals("PROCESSED", replay.status());
        Assertions.assertTrue(replay.eventId().startsWith("e-3-replay"));
        Assertions.assertEquals(1, dispatcher.stats().replayCount());
    }

    @Test
    void shouldReplayEventFromDlqAndRemoveIt() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setMaxRetries(0);

        EventRetryService retryService = new EventRetryService();
        EventStoreService storeService = new EventStoreService();
        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                retryService,
                storeService,
                event -> {},
                List.of()
        );

        dispatcher.process(new IntegrationEvent("e-4", "unknown", "databus", Instant.now(), Map.of()));
        Assertions.assertEquals(1, retryService.dlqSnapshot().size());

        EventDispatcherService replayDispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                retryService,
                storeService,
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        retryService.toDlq(new IntegrationEvent("e-5", "visit-created", "databus", Instant.now(), Map.of()));
        EventProcessingResult replay = replayDispatcher.replayFromDlq("e-5");

        Assertions.assertEquals("PROCESSED", replay.status());
        Assertions.assertNull(retryService.getById("e-5"));
    }

    @Test
    void shouldRejectEventWithPayloadTooLarge() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setMaxPayloadFields(1);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        IntegrationEvent event = new IntegrationEvent(
                "e-6",
                "visit-created",
                "databus",
                Instant.now(),
                Map.of("a", 1, "b", 2)
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> dispatcher.process(event));
    }

    @Test
    void shouldRejectEventWithFutureOccurredAt() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setMaxFutureSkewSeconds(10);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        IntegrationEvent event = new IntegrationEvent(
                "e-7",
                "visit-created",
                "databus",
                Instant.now().plusSeconds(60),
                Map.of()
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> dispatcher.process(event));
    }

    @Test
    void shouldReplayBulkFromDlqWithLimit() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setMaxRetries(0);

        EventRetryService retryService = new EventRetryService();
        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                retryService,
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        retryService.toDlq(new IntegrationEvent("e-8", "visit-created", "databus", Instant.now(), Map.of()));
        retryService.toDlq(new IntegrationEvent("e-9", "visit-created", "databus", Instant.now(), Map.of()));

        List<EventProcessingResult> results = dispatcher.replayAllFromDlq(1);
        Assertions.assertEquals(1, results.size());
        Assertions.assertEquals(1, retryService.size());
    }

    @Test
    void shouldResetStatsAndClearProcessedStore() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setMaxRetries(1);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        dispatcher.process(new IntegrationEvent("e-10", "visit-created", "databus", Instant.now(), Map.of()));
        Assertions.assertTrue(dispatcher.stats().processedCount() > 0);
        Assertions.assertTrue(dispatcher.stats().processedStoreSize() > 0);

        dispatcher.resetStats();
        dispatcher.clearProcessedStore();

        Assertions.assertEquals(0, dispatcher.stats().processedCount());
        Assertions.assertEquals(0, dispatcher.stats().processedStoreSize());
        Assertions.assertEquals(0, dispatcher.stats().inboxSize());
    }

    @Test
    void shouldReturnDegradedHealthWhenThresholdReached() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setMaxRetries(0);
        cfg.getEventing().setDlqWarnThreshold(1);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of()
        );

        dispatcher.process(new IntegrationEvent("e-11", "unknown", "databus", Instant.now(), Map.of()));
        Assertions.assertEquals("DEGRADED", dispatcher.health().status());
        Assertions.assertFalse(dispatcher.health().reasons().isEmpty());
    }

    @Test
    void shouldRunMaintenanceAndRemoveOldEvents() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setMaxRetries(0);
        cfg.getEventing().setRetentionSeconds(1);
        cfg.getEventing().setMaxDlqEvents(1);
        cfg.getEventing().setMaxProcessedEvents(1);

        EventRetryService retryService = new EventRetryService();
        EventStoreService storeService = new EventStoreService();
        EventInboxService inboxService = new EventInboxService();

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                inboxService,
                retryService,
                storeService,
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        Instant old = Instant.now().minusSeconds(10);
        retryService.toDlq(new IntegrationEvent("e-12", "visit-created", "databus", old, Map.of()));
        retryService.toDlq(new IntegrationEvent("e-13", "visit-created", "databus", Instant.now(), Map.of()));
        storeService.saveProcessed(new IntegrationEvent("e-14", "visit-created", "databus", old, Map.of()));
        inboxService.markIfFirst("e-14");

        EventingMaintenanceReport report = dispatcher.runMaintenance();

        Assertions.assertTrue(report.removedFromDlq() >= 1);
        Assertions.assertTrue(report.removedFromProcessed() >= 1);
        Assertions.assertEquals(0, report.statsAfter().processedStoreSize());
    }

    @Test
    void shouldExportAndImportSnapshot() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        dispatcher.process(new IntegrationEvent("e-15", "visit-created", "databus", Instant.now(), Map.of()));
        EventingSnapshot snapshot = dispatcher.exportSnapshot();
        Assertions.assertFalse(snapshot.processed().isEmpty());

        EventDispatcherService target = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        EventingImportResult result = target.importSnapshot(snapshot, true);
        Assertions.assertTrue(result.importedProcessed() >= 1);
        Assertions.assertNotNull(target.processedEvent("e-15"));
    }

    @Test
    void shouldValidateSnapshotImportLimit() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setSnapshotImportMaxEvents(1);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        EventingSnapshot snapshot = new EventingSnapshot(
                Map.of(
                        "a", new IntegrationEvent("a", "visit-created", "databus", Instant.now(), Map.of()),
                        "b", new IntegrationEvent("b", "visit-created", "databus", Instant.now(), Map.of())
                ),
                List.of(),
                dispatcher.stats()
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> dispatcher.importSnapshot(snapshot, false));
    }

    @Test
    void shouldProvideCapabilitiesAndPreviewImport() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        EventingSnapshot snapshot = new EventingSnapshot(Map.of(), List.of(), dispatcher.stats());
        EventingImportResult preview = dispatcher.previewImport(snapshot);

        Assertions.assertEquals(0, preview.importedProcessed());
        Assertions.assertTrue(dispatcher.capabilities().snapshotExportImport());
        Assertions.assertTrue(dispatcher.capabilities().maintenance());
    }

    @Test
    void shouldValidateSnapshotMismatchedProcessedKey() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setSnapshotImportRequireMatchingProcessedKeys(true);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        EventingSnapshot snapshot = new EventingSnapshot(
                Map.of("key-1", new IntegrationEvent("another-id", "visit-created", "databus", Instant.now(), Map.of())),
                List.of(),
                dispatcher.stats()
        );

        EventingSnapshotValidation validation = dispatcher.validateSnapshot(snapshot);
        Assertions.assertFalse(validation.valid());
        Assertions.assertTrue(validation.violations().stream().anyMatch(v -> "PROCESSED_KEY_EVENT_ID_MISMATCH".equals(v.code())));
    }

    @Test
    void shouldValidateSnapshotCrossListDuplicates() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        IntegrationEvent event = new IntegrationEvent("dup-1", "visit-created", "databus", Instant.now(), Map.of());
        EventingSnapshot snapshot = new EventingSnapshot(
                Map.of("dup-1", event),
                List.of(event),
                dispatcher.stats()
        );

        EventingSnapshotValidation validation = dispatcher.validateSnapshot(snapshot);
        Assertions.assertFalse(validation.valid());
        Assertions.assertTrue(validation.violations().stream().anyMatch(v -> "CROSS_LIST_DUPLICATE_EVENT_ID".equals(v.code())));
    }

    @Test
    void shouldPreviewImportWithClearProjection() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );
        dispatcher.process(new IntegrationEvent("base-1", "visit-created", "databus", Instant.now(), Map.of()));

        EventingSnapshot snapshot = new EventingSnapshot(
                Map.of("new-1", new IntegrationEvent("new-1", "visit-created", "databus", Instant.now(), Map.of())),
                List.of(new IntegrationEvent("dlq-1", "visit-created", "databus", Instant.now(), Map.of())),
                dispatcher.stats()
        );
        EventingImportResult preview = dispatcher.previewImport(snapshot, true);

        Assertions.assertEquals(1, preview.statsAfter().processedStoreSize());
        Assertions.assertEquals(1, preview.statsAfter().dlqSize());
        Assertions.assertEquals(1, preview.statsAfter().inboxSize());
    }

    @Test
    void shouldExposeSnapshotLimits() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setSnapshotImportMaxEvents(123);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        EventingLimits limits = dispatcher.limits();
        Assertions.assertEquals(123, limits.snapshotImportMaxEvents());
        Assertions.assertTrue(limits.requireMatchingProcessedKeys());
        Assertions.assertTrue(limits.rejectCrossListDuplicates());
    }

    @Test
    void shouldAllowLenientValidationMode() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );

        IntegrationEvent event = new IntegrationEvent("dup-2", "visit-created", "databus", Instant.now(), Map.of());
        EventingSnapshot snapshot = new EventingSnapshot(Map.of("another-key", event), List.of(event), dispatcher.stats());

        EventingSnapshotValidation strict = dispatcher.validateSnapshot(snapshot, true);
        EventingSnapshotValidation lenient = dispatcher.validateSnapshot(snapshot, false);

        Assertions.assertFalse(strict.valid());
        Assertions.assertTrue(lenient.valid());
    }

    @Test
    void shouldAnalyzeImportProjectionAndOverflow() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(true);
        cfg.getEventing().setMaxProcessedEvents(1);
        cfg.getEventing().setMaxDlqEvents(1);
        cfg.getEventing().setSnapshotImportMaxEvents(2);

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );
        dispatcher.process(new IntegrationEvent("base-2", "visit-created", "databus", Instant.now(), Map.of()));

        EventingSnapshot snapshot = new EventingSnapshot(
                Map.of("p-1", new IntegrationEvent("p-1", "visit-created", "databus", Instant.now(), Map.of())),
                List.of(new IntegrationEvent("d-1", "visit-created", "databus", Instant.now(), Map.of())),
                dispatcher.stats()
        );

        EventingImportAnalysis analysis = dispatcher.analyzeImport(snapshot, false, true);
        Assertions.assertTrue(analysis.valid());
        Assertions.assertTrue(analysis.projectedProcessedOverflow());
        Assertions.assertFalse(analysis.projectedDlqOverflow());
        Assertions.assertEquals(100, analysis.limitUsagePercent());
    }
}
