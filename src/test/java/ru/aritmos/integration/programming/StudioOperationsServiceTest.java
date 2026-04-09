package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.DefaultVisitCreatedEventHandler;
import ru.aritmos.integration.eventing.EventDispatcherService;
import ru.aritmos.integration.eventing.EventInboxService;
import ru.aritmos.integration.eventing.EventOutboxService;
import ru.aritmos.integration.eventing.EventRetryService;
import ru.aritmos.integration.eventing.EventStoreService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class StudioOperationsServiceTest {

    @Test
    void shouldExecuteClearDebugHistoryOperation(@TempDir Path tempDir) {
        ScriptDebugHistoryService debugHistory = new ScriptDebugHistoryService();
        debugHistory.record(new ScriptDebugHistoryService.DebugEntry(
                "s-1",
                Instant.parse("2026-03-01T10:00:00Z"),
                10,
                true,
                Map.of("ok", true),
                null,
                Map.of(),
                Map.of(),
                Map.of()
        ));

        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                debugHistory,
                buildWorkspaceService(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"))
        );

        Map<String, Object> result = service.execute("CLEAR_DEBUG_HISTORY", Map.of("scriptId", "s-1"), "tester");
        Assertions.assertEquals("CLEAR_DEBUG_HISTORY", result.get("operation"));
        Assertions.assertEquals(1, result.get("removed"));
    }

    @Test
    void shouldExecuteRefreshBootstrapOperation(@TempDir Path tempDir) {
        StudioEditorSettingsService settingsService = new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"));
        settingsService.save("tester", new ru.aritmos.integration.domain.StudioEditorSettingsDto("dark", 14, true, true, "s-1", null));

        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceService(),
                settingsService
        );

        Map<String, Object> result = service.execute("REFRESH_BOOTSTRAP", Map.of("debugHistoryLimit", 5), "tester");
        Assertions.assertEquals("REFRESH_BOOTSTRAP", result.get("operation"));
        Map<String, Object> snapshot = cast(result.get("snapshot"));
        Assertions.assertTrue(snapshot.containsKey("editorSettings"));
        Assertions.assertTrue(snapshot.containsKey("editorCapabilities"));
    }

    @Test
    void shouldRejectUnsupportedOperation(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceService(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"))
        );
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                service.execute("UNKNOWN_OP", Map.of(), "tester"));
    }

    @Test
    void shouldExposeOperationsCatalog(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceService(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"))
        );

        var catalog = service.catalog();
        Assertions.assertEquals(StudioOperationsService.Operation.values().length, catalog.size());
        Assertions.assertEquals("FLUSH_OUTBOX", catalog.get(0).operation());
        Assertions.assertEquals(Map.of("limit", 100), catalog.get(0).parameterTemplate());
    }

    private static EventDispatcherService buildDispatcher() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(false);
        return new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                new EventOutboxService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );
    }

    private static StudioWorkspaceService buildWorkspaceService() {
        return new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
