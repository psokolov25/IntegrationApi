package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.eventing.DefaultVisitCreatedEventHandler;
import ru.aritmos.integration.eventing.EventDispatcherService;
import ru.aritmos.integration.eventing.EventInboxService;
import ru.aritmos.integration.eventing.EventOutboxService;
import ru.aritmos.integration.eventing.EventRetryService;
import ru.aritmos.integration.eventing.EventStoreService;
import ru.aritmos.integration.service.BranchStateCache;

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

    @Test
    void shouldBuildInboxOutboxSnapshotOperation(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceService(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"))
        );

        Map<String, Object> result = service.execute("SNAPSHOT_INBOX_OUTBOX",
                Map.of("limit", 5, "status", "FAILED", "includeSent", true), "tester");
        Assertions.assertEquals("SNAPSHOT_INBOX_OUTBOX", result.get("operation"));
        Assertions.assertEquals(5, result.get("limit"));
        Assertions.assertEquals("FAILED", result.get("status"));
        Assertions.assertEquals(true, result.get("includeSent"));
        Map<String, Object> snapshot = cast(result.get("snapshot"));
        Assertions.assertTrue(snapshot.containsKey("inbox"));
        Assertions.assertTrue(snapshot.containsKey("outbox"));
    }

    @Test
    void shouldBuildVisitManagersAndExternalServicesSnapshot(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"))
        );

        Map<String, Object> vm = service.execute("SNAPSHOT_VISIT_MANAGERS", Map.of(), "tester");
        Assertions.assertEquals("SNAPSHOT_VISIT_MANAGERS", vm.get("operation"));
        Map<String, Object> vmSnapshot = cast(vm.get("snapshot"));
        Assertions.assertEquals(1, vmSnapshot.get("visitManagersCount"));

        Map<String, Object> ext = service.execute("SNAPSHOT_EXTERNAL_SERVICES", Map.of(), "tester");
        Assertions.assertEquals("SNAPSHOT_EXTERNAL_SERVICES", ext.get("operation"));
        Map<String, Object> extSnapshot = cast(ext.get("snapshot"));
        Assertions.assertEquals(1, extSnapshot.get("restServicesCount"));
        Assertions.assertEquals(1, extSnapshot.get("messageBrokersCount"));
    }

    @Test
    void shouldBuildBranchStateCacheSnapshotOperation(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"))
        );

        Map<String, Object> result = service.execute("SNAPSHOT_BRANCH_CACHE", Map.of("limit", 10), "tester");
        Assertions.assertEquals("SNAPSHOT_BRANCH_CACHE", result.get("operation"));
        Assertions.assertEquals(10, result.get("limit"));
        Map<String, Object> snapshot = cast(result.get("snapshot"));
        Assertions.assertEquals(true, snapshot.get("enabled"));
        Assertions.assertEquals(1, snapshot.get("total"));
    }

    @Test
    void shouldBuildRuntimeSettingsSnapshotOperation(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"))
        );

        Map<String, Object> result = service.execute("SNAPSHOT_RUNTIME_SETTINGS", Map.of(), "tester");
        Assertions.assertEquals("SNAPSHOT_RUNTIME_SETTINGS", result.get("operation"));
        Map<String, Object> snapshot = cast(result.get("snapshot"));
        Assertions.assertTrue(snapshot.containsKey("eventingEnabled"));
        Assertions.assertTrue(snapshot.containsKey("aggregateRequestTimeoutMillis"));
    }

    @Test
    void shouldExportEditorSettingsOperation(@TempDir Path tempDir) {
        StudioEditorSettingsService settingsService = new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"));
        settingsService.save("tester", new ru.aritmos.integration.domain.StudioEditorSettingsDto("dark", 15, true, true, "s-1", null));

        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                settingsService
        );

        Map<String, Object> exportResult = service.execute("EXPORT_EDITOR_SETTINGS", Map.of(), "tester");
        Assertions.assertEquals("EXPORT_EDITOR_SETTINGS", exportResult.get("operation"));
        Map<String, Object> exported = cast(exportResult.get("settingsBySubject"));
        Assertions.assertTrue(exported.containsKey("tester"));
        Assertions.assertTrue(exportResult.containsKey("capabilities"));
    }

    @Test
    void shouldPreviewMaintenanceAndExportEventingSnapshot(@TempDir Path tempDir) {
        EventDispatcherService dispatcher = buildDispatcher();

        StudioOperationsService service = new StudioOperationsService(
                dispatcher,
                new ScriptDebugHistoryService(),
                buildWorkspaceService(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"))
        );

        Map<String, Object> maintenance = service.execute("PREVIEW_EVENTING_MAINTENANCE", Map.of(), "tester");
        Assertions.assertEquals("PREVIEW_EVENTING_MAINTENANCE", maintenance.get("operation"));
        Assertions.assertTrue(maintenance.containsKey("report"));
        Assertions.assertTrue(maintenance.containsKey("stats"));

        Map<String, Object> snapshot = service.execute("EXPORT_EVENTING_SNAPSHOT", Map.of(), "tester");
        Assertions.assertEquals("EXPORT_EVENTING_SNAPSHOT", snapshot.get("operation"));
        Assertions.assertTrue(snapshot.containsKey("snapshot"));
        Assertions.assertTrue(snapshot.containsKey("health"));
    }

    @Test
    void shouldBuildDashboardSnapshotOperation(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"))
        );

        Map<String, Object> dashboard = service.execute("DASHBOARD_SNAPSHOT",
                Map.of("debugHistoryLimit", 15), "tester");
        Assertions.assertEquals("DASHBOARD_SNAPSHOT", dashboard.get("operation"));
        Assertions.assertEquals(15, dashboard.get("debugHistoryLimit"));
        Map<String, Object> snapshot = cast(dashboard.get("snapshot"));
        Assertions.assertTrue(snapshot.containsKey("workspace"));
        Assertions.assertTrue(snapshot.containsKey("inboxOutbox"));
        Assertions.assertTrue(snapshot.containsKey("visitManagers"));
        Assertions.assertTrue(snapshot.containsKey("branchStateCache"));
        Assertions.assertTrue(snapshot.containsKey("externalServices"));
        Assertions.assertTrue(snapshot.containsKey("runtimeSettings"));
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

    private static StudioWorkspaceService buildWorkspaceServiceWithConnectors() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost:8081");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));

        IntegrationGatewayConfiguration.ExternalRestServiceSettings rest = new IntegrationGatewayConfiguration.ExternalRestServiceSettings();
        rest.setId("ext-crm");
        rest.setBaseUrl("http://localhost:19090");
        rest.setDefaultHeaders(Map.of("X-API-KEY", "dev"));
        cfg.getProgrammableApi().setExternalRestServices(List.of(rest));

        IntegrationGatewayConfiguration.MessageBrokerSettings broker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        broker.setId("broker-1");
        broker.setType("LOGGING");
        broker.setEnabled(true);
        cfg.getProgrammableApi().setMessageBrokers(List.of(broker));

        BranchStateCache cache = new BranchStateCache(cfg);
        cache.put(new BranchStateDto(
                "BR-101",
                "vm-main",
                "OPEN",
                "09:00-18:00",
                2,
                Instant.parse("2026-04-05T10:00:00Z"),
                false,
                "system:eventing"
        ));

        return new StudioWorkspaceService(
                cfg,
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(),
                cache
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
