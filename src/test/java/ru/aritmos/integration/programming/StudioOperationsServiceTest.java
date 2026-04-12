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
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
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
                settingsService,
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
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
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
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
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
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
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
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
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
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
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
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
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
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
                settingsService,
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
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
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
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
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
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

    @Test
    void shouldPreviewHttpProcessingOperation(@TempDir Path tempDir) {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getProgrammableApi().getHttpProcessing().setRequestEnvelopeEnabled(true);
        cfg.getProgrammableApi().getHttpProcessing().setResponseBodyMaxChars(8);

        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                new ProgrammableHttpExchangeProcessor(cfg, new ObjectMapper()),
                cfg,
                buildAdapters()
        );

        Map<String, Object> result = service.execute("PREVIEW_HTTP_PROCESSING", Map.of(
                "direction", "INBOUND_SUO",
                "headers", Map.of("X-Req", "1"),
                "body", Map.of("visitId", "V-1"),
                "responseStatus", 202,
                "responseBody", "{\"ok\":true,\"v\":1}",
                "responseHeaders", Map.of("Content-Type", List.of("application/json"))
        ), "tester");
        Assertions.assertEquals("PREVIEW_HTTP_PROCESSING", result.get("operation"));
        Map<String, Object> preview = cast(result.get("preview"));
        Assertions.assertEquals("INBOUND_SUO", preview.get("direction"));

        Map<String, String> requestHeaders = castStringMap(preview.get("requestHeaders"));
        Assertions.assertEquals("1", requestHeaders.get("X-Req"));
        Assertions.assertEquals("INBOUND_SUO", requestHeaders.get("X-Integration-Direction"));

        Map<String, Object> requestBody = cast(preview.get("requestBody"));
        Assertions.assertTrue(requestBody.containsKey("meta"));
        Assertions.assertTrue(requestBody.containsKey("data"));

        Map<String, Object> response = cast(preview.get("response"));
        Assertions.assertEquals(202, response.get("status"));
        Assertions.assertEquals("{\"ok\":tr...", response.get("bodyPreview"));
        Assertions.assertEquals(true, cast(response.get("processingMeta")).get("jsonParsed"));
        Assertions.assertEquals(List.of("OUTBOUND_EXTERNAL", "INBOUND_SUO"), result.get("supportedDirections"));
    }

    @Test
    void shouldExportPreviewAndApplyHttpProcessingProfile(@TempDir Path tempDir) {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getProgrammableApi().getHttpProcessing().setDirectionHeaderName("X-Old");
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                new ProgrammableHttpExchangeProcessor(cfg, new ObjectMapper()),
                cfg,
                buildAdapters()
        );

        Map<String, Object> exported = service.execute("EXPORT_HTTP_PROCESSING_PROFILE", Map.of(), "tester");
        Assertions.assertEquals("EXPORT_HTTP_PROCESSING_PROFILE", exported.get("operation"));
        Assertions.assertEquals("X-Old", cast(exported.get("httpProcessingProfile")).get("directionHeaderName"));

        Map<String, Object> invalidPreview = service.execute("IMPORT_HTTP_PROCESSING_PROFILE_PREVIEW", Map.of(
                "httpProcessingProfile", Map.of("addDirectionHeader", true, "directionHeaderName", "", "responseBodyMaxChars", 0)
        ), "tester");
        Assertions.assertEquals(false, invalidPreview.get("valid"));

        Map<String, Object> applied = service.execute("IMPORT_HTTP_PROCESSING_PROFILE_APPLY", Map.of(
                "httpProcessingProfile", Map.of(
                        "enabled", true,
                        "addDirectionHeader", true,
                        "directionHeaderName", "X-New",
                        "requestEnvelopeEnabled", true,
                        "parseJsonBody", true,
                        "responseBodyMaxChars", 4096
                )
        ), "tester");
        Assertions.assertEquals(true, applied.get("applied"));
        Assertions.assertEquals("X-New", cast(applied.get("httpProcessingProfile")).get("directionHeaderName"));
    }

    @Test
    void shouldBuildHttpProcessingMatrixPreview(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
        );

        Map<String, Object> result = service.execute("PREVIEW_HTTP_PROCESSING_MATRIX", Map.of(
                "headers", Map.of("X-Req", "1"),
                "body", Map.of("visitId", "V-1")
        ), "tester");
        Assertions.assertEquals("PREVIEW_HTTP_PROCESSING_MATRIX", result.get("operation"));
        List<Map<String, Object>> matrix = castListOfMaps(result.get("directionPreviews"));
        Assertions.assertEquals(2, matrix.size());
        Assertions.assertTrue(matrix.stream().anyMatch(item -> "OUTBOUND_EXTERNAL".equals(item.get("direction"))));
        Assertions.assertTrue(matrix.stream().anyMatch(item -> "INBOUND_SUO".equals(item.get("direction"))));
    }

    @Test
    void shouldPreviewConnectorProfileOperation(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
        );

        Map<String, Object> preview = service.execute("PREVIEW_CONNECTOR_PROFILE", Map.of("brokerType", "mqtt"), "tester");
        Assertions.assertEquals("PREVIEW_CONNECTOR_PROFILE", preview.get("operation"));
        Assertions.assertEquals("MQTT", preview.get("brokerType"));
        Assertions.assertEquals(true, preview.get("adapterAvailable"));
        Map<String, Object> profile = cast(preview.get("profile"));
        Assertions.assertEquals("MQTT", profile.get("type"));
    }

    @Test
    void shouldValidateConnectorConfigAgainstProfile(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
        );

        Map<String, Object> validResult = service.execute("VALIDATE_CONNECTOR_CONFIG", Map.of(
                "brokerType", "WEBHOOK_HTTP",
                "properties", Map.of("url", "https://gateway.local/events", "method", "POST")
        ), "tester");
        Assertions.assertEquals("VALIDATE_CONNECTOR_CONFIG", validResult.get("operation"));
        Assertions.assertEquals(true, validResult.get("valid"));

        Map<String, Object> invalidResult = service.execute("VALIDATE_CONNECTOR_CONFIG", Map.of(
                "brokerType", "WEBHOOK_HTTP",
                "properties", Map.of("method", "POST")
        ), "tester");
        Assertions.assertEquals(false, invalidResult.get("valid"));
        Assertions.assertEquals(List.of("url"), invalidResult.get("missingRequiredProperties"));
    }

    @Test
    void shouldExportAndPreviewImportConnectorPresets(@TempDir Path tempDir) {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.MessageBrokerSettings existingBroker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        existingBroker.setId("webhook-bus");
        existingBroker.setType("WEBHOOK_HTTP");
        existingBroker.setProperties(Map.of("url", "https://existing.local/events"));
        cfg.getProgrammableApi().setMessageBrokers(List.of(existingBroker));
        IntegrationGatewayConfiguration.ExternalRestServiceSettings existingRest = new IntegrationGatewayConfiguration.ExternalRestServiceSettings();
        existingRest.setId("crm");
        existingRest.setBaseUrl("https://existing-crm.local");
        cfg.getProgrammableApi().setExternalRestServices(List.of(existingRest));

        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                cfg,
                buildAdapters()
        );

        Map<String, Object> exported = service.execute("EXPORT_CONNECTOR_PRESETS", Map.of(), "tester");
        Assertions.assertEquals("EXPORT_CONNECTOR_PRESETS", exported.get("operation"));
        Assertions.assertEquals(2, cast(exported.get("metadata")).get("formatVersion"));
        Assertions.assertTrue(cast(exported.get("connectorPresets")).containsKey("supportedBrokerProfiles"));

        Map<String, Object> preview = service.execute("IMPORT_CONNECTOR_PRESETS_PREVIEW", Map.of(
                "messageBrokers", List.of(
                        Map.of("id", "webhook-bus", "type", "WEBHOOK_HTTP", "properties", Map.of("url", "https://gateway.local/events")),
                        Map.of("id", "webhook-bus", "type", "WEBHOOK_HTTP", "properties", Map.of("url", "https://gateway.local/events-2")),
                        Map.of("id", "broken-bus", "type", "WEBHOOK_HTTP", "properties", Map.of("method", "POST"))
                ),
                "externalRestServices", List.of(
                        Map.of("id", "crm", "baseUrl", "https://crm.local"),
                        Map.of("id", "crm", "baseUrl", "https://crm-copy.local"),
                        Map.of("id", "", "baseUrl", "bad-url")
                )
        ), "tester");
        Assertions.assertEquals("IMPORT_CONNECTOR_PRESETS_PREVIEW", preview.get("operation"));
        Map<String, Object> summary = cast(preview.get("summary"));
        Assertions.assertEquals(3, summary.get("brokersTotal"));
        Assertions.assertEquals(3L, summary.get("brokersInvalid"));
        Assertions.assertEquals(2L, summary.get("brokersConflictsWithExisting"));
        Assertions.assertEquals(1, summary.get("brokersDuplicatesInImport"));
        Assertions.assertEquals(3, summary.get("restServicesTotal"));
        Assertions.assertEquals(3L, summary.get("restServicesInvalid"));
        Assertions.assertEquals(2L, summary.get("restServicesConflictsWithExisting"));
        Assertions.assertEquals(1, summary.get("restServicesDuplicatesInImport"));
        Assertions.assertEquals(false, summary.get("importable"));
    }

    @Test
    void shouldApplyImportConnectorPresets(@TempDir Path tempDir) {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.MessageBrokerSettings existingBroker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        existingBroker.setId("existing");
        existingBroker.setType("LOGGING");
        existingBroker.setEnabled(true);
        cfg.getProgrammableApi().setMessageBrokers(List.of(existingBroker));

        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                cfg,
                buildAdapters()
        );

        Map<String, Object> result = service.execute("IMPORT_CONNECTOR_PRESETS_APPLY", Map.of(
                "replaceExisting", false,
                "messageBrokers", List.of(
                        Map.of("id", "webhook-bus", "type", "WEBHOOK_HTTP", "properties", Map.of("url", "https://gateway.local/events"))
                ),
                "externalRestServices", List.of(
                        Map.of("id", "crm", "baseUrl", "https://crm.local")
                )
        ), "tester");
        Assertions.assertEquals("IMPORT_CONNECTOR_PRESETS_APPLY", result.get("operation"));
        Assertions.assertEquals(true, result.get("applied"));
        Assertions.assertEquals(1, result.get("appliedMessageBrokers"));
        Assertions.assertEquals(1, result.get("appliedExternalRestServices"));
        Assertions.assertTrue(cast(result.get("rollbackSnapshot")).containsKey("messageBrokers"));

        Map<String, Object> totals = cast(result.get("totalsAfterApply"));
        Assertions.assertEquals(2, totals.get("messageBrokers"));
        Assertions.assertEquals(1, totals.get("externalRestServices"));
    }

    @Test
    void shouldRejectApplyImportWhenPreviewInvalid(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
        );

        Map<String, Object> result = service.execute("IMPORT_CONNECTOR_PRESETS_APPLY", Map.of(
                "messageBrokers", List.of(
                        Map.of("id", "bad", "type", "WEBHOOK_HTTP", "properties", Map.of("method", "POST"))
                )
        ), "tester");
        Assertions.assertEquals("IMPORT_CONNECTOR_PRESETS_APPLY", result.get("operation"));
        Assertions.assertEquals(false, result.get("applied"));
        Assertions.assertTrue(cast(result.get("preview")).containsKey("summary"));
    }

    @Test
    void shouldBuildImportConnectorPresetsDiff(@TempDir Path tempDir) {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.MessageBrokerSettings existingBroker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        existingBroker.setId("webhook-bus");
        existingBroker.setType("WEBHOOK_HTTP");
        existingBroker.setProperties(Map.of("url", "https://old.local/events"));
        cfg.getProgrammableApi().setMessageBrokers(List.of(existingBroker));
        IntegrationGatewayConfiguration.ExternalRestServiceSettings existingRest = new IntegrationGatewayConfiguration.ExternalRestServiceSettings();
        existingRest.setId("crm");
        existingRest.setBaseUrl("https://crm.local");
        cfg.getProgrammableApi().setExternalRestServices(List.of(existingRest));

        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                cfg,
                buildAdapters()
        );

        Map<String, Object> result = service.execute("IMPORT_CONNECTOR_PRESETS_DIFF", Map.of(
                "messageBrokers", List.of(
                        Map.of("id", "webhook-bus", "type", "WEBHOOK_HTTP", "properties", Map.of("url", "https://new.local/events")),
                        Map.of("id", "kafka-bus", "type", "KAFKA", "properties", Map.of("bootstrapServers", "kafka:9092"))
                ),
                "externalRestServices", List.of(
                        Map.of("id", "crm", "baseUrl", "https://crm.local"),
                        Map.of("id", "billing", "baseUrl", "https://billing.local")
                )
        ), "tester");

        Assertions.assertEquals("IMPORT_CONNECTOR_PRESETS_DIFF", result.get("operation"));
        Map<String, Object> summary = cast(result.get("summary"));
        Assertions.assertEquals(1L, summary.get("messageBrokersCreate"));
        Assertions.assertEquals(1L, summary.get("messageBrokersUpdate"));
        Assertions.assertEquals(1L, summary.get("externalRestServicesCreate"));
        Assertions.assertEquals(0L, summary.get("externalRestServicesUpdate"));
    }

    @Test
    void shouldExportPreviewAndApplyIntegrationConnectorBundle(@TempDir Path tempDir) {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getProgrammableApi().getHttpProcessing().setDirectionHeaderName("X-Before");
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                new ProgrammableHttpExchangeProcessor(cfg, new ObjectMapper()),
                cfg,
                buildAdapters()
        );

        Map<String, Object> exported = service.execute("EXPORT_INTEGRATION_CONNECTOR_BUNDLE", Map.of(), "tester");
        Assertions.assertEquals("EXPORT_INTEGRATION_CONNECTOR_BUNDLE", exported.get("operation"));
        Map<String, Object> bundle = cast(exported.get("bundle"));
        Assertions.assertTrue(bundle.containsKey("httpProcessingProfile"));
        Assertions.assertTrue(bundle.containsKey("connectorPresets"));

        Map<String, Object> preview = service.execute("IMPORT_INTEGRATION_CONNECTOR_BUNDLE_PREVIEW", Map.of(
                "bundle", Map.of(
                        "httpProcessingProfile", Map.of(
                                "enabled", true,
                                "addDirectionHeader", true,
                                "directionHeaderName", "X-Bundle",
                                "requestEnvelopeEnabled", true,
                                "parseJsonBody", true,
                                "responseBodyMaxChars", 1024
                        ),
                        "connectorPresets", Map.of(
                                "messageBrokers", List.of(Map.of("id", "webhook-bus", "type", "WEBHOOK_HTTP",
                                        "properties", Map.of("url", "https://gateway.local/events"))),
                                "externalRestServices", List.of(Map.of("id", "crm", "baseUrl", "https://crm.local"))
                        )
                )
        ), "tester");
        Assertions.assertEquals("IMPORT_INTEGRATION_CONNECTOR_BUNDLE_PREVIEW", preview.get("operation"));
        Assertions.assertEquals(true, preview.get("importable"));

        Map<String, Object> apply = service.execute("IMPORT_INTEGRATION_CONNECTOR_BUNDLE_APPLY", Map.of(
                "replaceExisting", false,
                "bundle", Map.of(
                        "httpProcessingProfile", Map.of(
                                "enabled", true,
                                "addDirectionHeader", true,
                                "directionHeaderName", "X-Bundle",
                                "requestEnvelopeEnabled", true,
                                "parseJsonBody", true,
                                "responseBodyMaxChars", 1024
                        ),
                        "connectorPresets", Map.of(
                                "messageBrokers", List.of(Map.of("id", "webhook-bus", "type", "WEBHOOK_HTTP",
                                        "properties", Map.of("url", "https://gateway.local/events"))),
                                "externalRestServices", List.of(Map.of("id", "crm", "baseUrl", "https://crm.local"))
                        )
                )
        ), "tester");
        Assertions.assertEquals("IMPORT_INTEGRATION_CONNECTOR_BUNDLE_APPLY", apply.get("operation"));
        Assertions.assertEquals(true, apply.get("applied"));
        Assertions.assertEquals("X-Bundle", cfg.getProgrammableApi().getHttpProcessing().getDirectionHeaderName());
        Assertions.assertEquals(1, cfg.getProgrammableApi().getMessageBrokers().size());
        Assertions.assertEquals(1, cfg.getProgrammableApi().getExternalRestServices().size());
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

    private static ProgrammableHttpExchangeProcessor buildProcessor() {
        return new ProgrammableHttpExchangeProcessor(new IntegrationGatewayConfiguration(), new ObjectMapper());
    }

    private static List<CustomerMessageBusAdapter> buildAdapters() {
        return List.of(
                new LoggingMessageBusAdapter(),
                new HttpWebhookMessageBusAdapter(new ObjectMapper())
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

    @SuppressWarnings("unchecked")
    private static Map<String, String> castStringMap(Object value) {
        return (Map<String, String>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castListOfMaps(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
