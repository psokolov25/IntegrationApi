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

import java.io.IOException;
import java.net.InetSocketAddress;
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
    void shouldExportPreviewAndApplyDebugHistoryOperations(@TempDir Path tempDir) {
        ScriptDebugHistoryService debugHistory = new ScriptDebugHistoryService();
        debugHistory.record(new ScriptDebugHistoryService.DebugEntry(
                "script-a",
                Instant.parse("2026-03-01T10:00:00Z"),
                12,
                true,
                Map.of("ok", true),
                "",
                Map.of("payload", "x"),
                Map.of("p", 1),
                Map.of("traceId", "t-1")
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

        Map<String, Object> exported = service.execute("EXPORT_DEBUG_HISTORY", Map.of("scriptId", "script-a", "limit", 10), "tester");
        Assertions.assertEquals("EXPORT_DEBUG_HISTORY", exported.get("operation"));
        Assertions.assertEquals(1, exported.get("total"));
        List<Map<String, Object>> entries = castListOfMaps(exported.get("entries"));
        Assertions.assertEquals("script-a", entries.get(0).get("scriptId"));

        Map<String, Object> preview = service.execute("IMPORT_DEBUG_HISTORY_PREVIEW", Map.of("entries", entries), "tester");
        Assertions.assertEquals("IMPORT_DEBUG_HISTORY_PREVIEW", preview.get("operation"));
        Assertions.assertEquals(true, preview.get("valid"));

        Map<String, Object> apply = service.execute("IMPORT_DEBUG_HISTORY_APPLY", Map.of(
                "replaceExisting", true,
                "entries", entries
        ), "tester");
        Assertions.assertEquals("IMPORT_DEBUG_HISTORY_APPLY", apply.get("operation"));
        Assertions.assertEquals(true, apply.get("applied"));
        Assertions.assertEquals(1, apply.get("imported"));
    }

    @Test
    void shouldRedactSensitiveFieldsInExportDebugHistory(@TempDir Path tempDir) {
        ScriptDebugHistoryService debugHistory = new ScriptDebugHistoryService();
        debugHistory.record(new ScriptDebugHistoryService.DebugEntry(
                "script-secure",
                Instant.parse("2026-03-01T10:05:00Z"),
                9,
                true,
                Map.of("apiToken", "t-123"),
                "",
                Map.of("password", "p-1", "profile", Map.of("secretKey", "k-1")),
                Map.of("authorization", "Bearer abc"),
                Map.of("safe", "ok")
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

        Map<String, Object> exported = service.execute("EXPORT_DEBUG_HISTORY", Map.of(
                "scriptId", "script-secure",
                "limit", 10,
                "redactSensitive", true
        ), "tester");
        List<Map<String, Object>> entries = castListOfMaps(exported.get("entries"));
        Map<String, Object> first = entries.get(0);
        Assertions.assertEquals("***", cast(first.get("payload")).get("password"));
        Assertions.assertEquals("***", cast(cast(first.get("payload")).get("profile")).get("secretKey"));
        Assertions.assertEquals("***", cast(first.get("parameters")).get("authorization"));
        Assertions.assertEquals("***", cast(first.get("result")).get("apiToken"));
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
    void shouldProbeExternalRestServiceOperation(@TempDir Path tempDir) throws IOException {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
            IntegrationGatewayConfiguration.ExternalRestServiceSettings rest = new IntegrationGatewayConfiguration.ExternalRestServiceSettings();
            rest.setId("crm");
            rest.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            cfg.getProgrammableApi().setExternalRestServices(List.of(rest));

            StudioOperationsService service = new StudioOperationsService(
                    buildDispatcher(),
                    new ScriptDebugHistoryService(),
                    new StudioWorkspaceService(cfg, new EventInboxService(), new EventOutboxService(),
                            new InMemoryGroovyScriptStorage(), new ScriptDebugHistoryService(), List.of()),
                    new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                    buildProcessor(),
                    cfg,
                    buildAdapters()
            );

            Map<String, Object> result = service.execute("PROBE_EXTERNAL_REST_SERVICE",
                    Map.of("serviceId", "crm", "path", "/health", "method", "GET", "timeoutMillis", 1500),
                    "tester");
            Assertions.assertEquals("PROBE_EXTERNAL_REST_SERVICE", result.get("operation"));
            Map<String, Object> probe = cast(result.get("probe"));
            Assertions.assertEquals(true, probe.get("ok"));
            Assertions.assertEquals("UP", probe.get("status"));
            Assertions.assertEquals(200, probe.get("statusCode"));
        } finally {
            server.stop(0);
        }
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

        Map<String, Object> invalidMethodResult = service.execute("VALIDATE_CONNECTOR_CONFIG", Map.of(
                "brokerType", "WEBHOOK_HTTP",
                "properties", Map.of("url", "https://gateway.local/events", "method", "DELETE")
        ), "tester");
        Assertions.assertEquals(false, invalidMethodResult.get("valid"));
        Assertions.assertEquals(
                List.of("Для WEBHOOK_HTTP поддерживаются только методы POST, PUT или PATCH"),
                invalidMethodResult.get("adapterValidationErrors")
        );
    }

    @Test
    void shouldGenerateOpenApiGroovyClientsFromUrl(@TempDir Path tempDir) throws IOException {
        String openApi = """
                openapi: 3.0.1
                info:
                  title: VisitManager Dev API
                  version: "1.2.3"
                servers:
                  - url: https://visitmanager.dev.local
                paths:
                  /api/v1/branches/{branchId}/state:
                    get:
                      operationId: getBranchState
                      summary: Получить состояние филиала
                    put:
                      operationId: updateBranchState
                      summary: Обновить состояние филиала
                  /api/v1/queues:
                    get:
                      operationId: getQueues
                      summary: Список очередей
                """;
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/openapi.yml", exchange -> {
            byte[] response = openApi.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/yaml");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            StudioOperationsService service = new StudioOperationsService(
                    buildDispatcher(),
                    new ScriptDebugHistoryService(),
                    buildWorkspaceServiceWithConnectors(),
                    new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                    buildProcessor(),
                    new IntegrationGatewayConfiguration(),
                    buildAdapters()
            );

            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/openapi.yml";
            Map<String, Object> result = service.execute("GENERATE_OPENAPI_REST_CLIENTS", Map.of(
                    "openApiUrl", url,
                    "serviceId", "visit-manager-dev"
            ), "tester");

            Assertions.assertEquals("GENERATE_OPENAPI_REST_CLIENTS", result.get("operation"));
            Map<String, Object> generated = cast(result.get("generated"));
            Assertions.assertEquals("visit-manager-dev", generated.get("serviceId"));
            Assertions.assertEquals("VisitManager Dev API", generated.get("serviceTitle"));
            Assertions.assertEquals("1.2.3", generated.get("serviceVersion"));
            List<Map<String, Object>> clients = castListOfMaps(generated.get("clients"));
            Assertions.assertEquals(3, clients.size());
            Assertions.assertTrue(clients.stream().anyMatch(item -> "GET".equals(item.get("httpMethod"))
                    && "/api/v1/branches/{branchId}/state".equals(item.get("path"))));
            Assertions.assertTrue(clients.stream().anyMatch(item -> "updateBranchState".equals(item.get("operationId"))));
            List<Map<String, Object>> scripts = castListOfMaps(generated.get("scripts"));
            Assertions.assertEquals(3, scripts.size());
            Assertions.assertTrue(scripts.stream().anyMatch(item ->
                    String.valueOf(item.get("scriptBody")).contains("externalRestClient.invoke")));
            Assertions.assertTrue(scripts.stream().allMatch(item -> item.containsKey("saveScriptRequest")));
            Map<String, Object> toolkit = cast(generated.get("toolkit"));
            Assertions.assertTrue(toolkit.containsKey("connectorPresetsPreviewRequest"));
            Assertions.assertTrue(toolkit.containsKey("connectorPresetsApplyRequest"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldApplyGeneratedOpenApiToolkitToRuntime(@TempDir Path tempDir) throws IOException {
        String openApi = """
                openapi: 3.0.1
                info:
                  title: VisitManager Dev API
                  version: "1.2.3"
                servers:
                  - url: https://visitmanager.dev.local
                paths:
                  /api/v1/queues:
                    get:
                      operationId: getQueues
                      summary: Список очередей
                """;
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/openapi.yml", exchange -> {
            byte[] response = openApi.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
            StudioOperationsService service = new StudioOperationsService(
                    buildDispatcher(),
                    new ScriptDebugHistoryService(),
                    buildWorkspaceServiceWithConnectors(),
                    new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                    buildProcessor(),
                    cfg,
                    buildAdapters(),
                    new InMemoryGroovyScriptStorage()
            );
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/openapi.yml";
            Map<String, Object> generated = cast(service.execute("GENERATE_OPENAPI_REST_CLIENTS", Map.of(
                    "openApiUrl", url,
                    "serviceId", "visit-manager-dev"
            ), "tester").get("generated"));

            Map<String, Object> applied = service.execute("APPLY_OPENAPI_REST_CLIENTS_TOOLKIT", Map.of(
                    "generated", generated,
                    "replaceExisting", false
            ), "tester");

            Assertions.assertEquals("APPLY_OPENAPI_REST_CLIENTS_TOOLKIT", applied.get("operation"));
            Assertions.assertEquals(1, applied.get("appliedExternalRestServices"));
            Assertions.assertEquals(1, applied.get("savedScripts"));
            Assertions.assertEquals(0, applied.get("skippedExistingScripts"));
            Assertions.assertEquals(List.of(), applied.get("invalidScripts"));
            Assertions.assertEquals("visit-manager-dev",
                    cfg.getProgrammableApi().getExternalRestServices().get(0).getId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreviewGeneratedOpenApiToolkitApply(@TempDir Path tempDir) {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        InMemoryGroovyScriptStorage storage = new InMemoryGroovyScriptStorage();
        storage.save(new StoredGroovyScript(
                "openapi-visit-manager-get-queues",
                GroovyScriptType.VISIT_MANAGER_ACTION,
                "return [cached:true]",
                "existing",
                Instant.parse("2026-01-10T10:00:00Z"),
                "tester"
        ));
        IntegrationGatewayConfiguration.ExternalRestServiceSettings existingService =
                new IntegrationGatewayConfiguration.ExternalRestServiceSettings();
        existingService.setId("visit-manager");
        existingService.setBaseUrl("https://existing.local");
        cfg.getProgrammableApi().setExternalRestServices(List.of(existingService));

        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                cfg,
                buildAdapters(),
                storage
        );

        Map<String, Object> previewResponse = service.execute("PREVIEW_OPENAPI_REST_CLIENTS_TOOLKIT_APPLY", Map.of(
                "replaceExisting", false,
                "generated", Map.of(
                        "serviceId", "visit-manager",
                        "externalRestServicePreset", Map.of("id", "visit-manager", "baseUrl", "https://visitmanager.local"),
                        "scripts", List.of(Map.of(
                                "scriptId", "openapi-visit-manager-get-queues",
                                "saveScriptRequest", Map.of(
                                        "scriptId", "openapi-visit-manager-get-queues",
                                        "type", "VISIT_MANAGER_ACTION",
                                        "scriptBody", "return [ok: true]"
                                )
                        ))
                )
        ), "tester");

        Assertions.assertEquals("PREVIEW_OPENAPI_REST_CLIENTS_TOOLKIT_APPLY", previewResponse.get("operation"));
        Map<String, Object> preview = cast(previewResponse.get("preview"));
        Map<String, Object> externalServicePreview = cast(preview.get("externalRestService"));
        Assertions.assertEquals("SKIP_EXISTS", externalServicePreview.get("decision"));
        Assertions.assertEquals(false, externalServicePreview.get("willApply"));
        Map<String, Object> summary = cast(preview.get("summary"));
        Assertions.assertEquals(0, summary.get("scriptsWillSave"));
        Assertions.assertEquals(1, summary.get("scriptsSkippedExisting"));
        Assertions.assertEquals(0, summary.get("scriptsInvalid"));
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
    void shouldSkipInvalidGeneratedGroovyScriptsWhenApplyingToolkit(@TempDir Path tempDir) {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                cfg,
                buildAdapters(),
                new InMemoryGroovyScriptStorage()
        );

        Map<String, Object> generated = Map.of(
                "serviceId", "vm-dev",
                "externalRestServicePreset", Map.of("id", "vm-dev", "baseUrl", "https://visitmanager.local"),
                "scripts", List.of(
                        Map.of(
                                "scriptId", "broken-script",
                                "saveScriptRequest", Map.of(
                                        "scriptId", "broken-script",
                                        "type", "VISIT_MANAGER_ACTION",
                                        "scriptBody", "return [broken: true",
                                        "description", "broken"
                                )
                        )
                )
        );

        Map<String, Object> applied = service.execute("APPLY_OPENAPI_REST_CLIENTS_TOOLKIT", Map.of(
                "generated", generated,
                "replaceExisting", false
        ), "tester");
        Assertions.assertEquals("APPLY_OPENAPI_REST_CLIENTS_TOOLKIT", applied.get("operation"));
        Assertions.assertEquals(0, applied.get("savedScripts"));
        List<Map<String, Object>> invalid = castListOfMaps(applied.get("invalidScripts"));
        Assertions.assertEquals(1, invalid.size());
        Assertions.assertEquals("broken-script", invalid.get(0).get("scriptId"));
    }

    @Test
    void shouldPreviewApplyingOpenApiToolkitInDryRunMode(@TempDir Path tempDir) {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        InMemoryGroovyScriptStorage storage = new InMemoryGroovyScriptStorage();
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                cfg,
                buildAdapters(),
                storage
        );

        Map<String, Object> generated = Map.of(
                "serviceId", "vm-dev-dry",
                "externalRestServicePreset", Map.of("id", "vm-dev-dry", "baseUrl", "https://visitmanager.local"),
                "scripts", List.of(
                        Map.of(
                                "scriptId", "openapi-vm-dev-dry-get-queues",
                                "saveScriptRequest", Map.of(
                                        "scriptId", "openapi-vm-dev-dry-get-queues",
                                        "type", "VISIT_MANAGER_ACTION",
                                        "scriptBody", "return [ok: true]",
                                        "description", "dry-run"
                                )
                        )
                )
        );

        Map<String, Object> applied = service.execute("APPLY_OPENAPI_REST_CLIENTS_TOOLKIT", Map.of(
                "generated", generated,
                "replaceExisting", false,
                "dryRun", true
        ), "tester");

        Assertions.assertEquals("APPLY_OPENAPI_REST_CLIENTS_TOOLKIT", applied.get("operation"));
        Assertions.assertEquals(true, applied.get("dryRun"));
        Assertions.assertEquals(1, applied.get("appliedExternalRestServices"));
        Assertions.assertEquals(1, applied.get("savedScripts"));
        Assertions.assertEquals(List.of(), applied.get("invalidScripts"));
        Map<String, Object> preview = cast(applied.get("preview"));
        Map<String, Object> summary = cast(preview.get("summary"));
        Assertions.assertEquals(1, summary.get("scriptsWillSave"));
        Assertions.assertEquals(true, summary.get("externalRestServiceWillApply"));
        Assertions.assertTrue(cfg.getProgrammableApi().getExternalRestServices().isEmpty(), "dry-run не должен менять runtime services");
        Assertions.assertNull(storage.get("openapi-vm-dev-dry-get-queues"), "dry-run не должен сохранять скрипты");
    }

    @Test
    void shouldValidateGroovyScriptBodyViaStudioOperation(@TempDir Path tempDir) {
        StudioOperationsService service = new StudioOperationsService(
                buildDispatcher(),
                new ScriptDebugHistoryService(),
                buildWorkspaceServiceWithConnectors(),
                new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json")),
                buildProcessor(),
                new IntegrationGatewayConfiguration(),
                buildAdapters()
        );

        Map<String, Object> valid = service.execute("VALIDATE_GROOVY_SCRIPT_BODY", Map.of(
                "type", "VISIT_MANAGER_ACTION",
                "scriptBody", "return [ok: true]"
        ), "tester");
        Assertions.assertEquals("VALIDATE_GROOVY_SCRIPT_BODY", valid.get("operation"));
        Assertions.assertEquals(true, valid.get("ok"));
        Assertions.assertTrue(((List<?>) valid.get("bindingHints")).contains("visitManagerInvoker"));

        Map<String, Object> invalid = service.execute("VALIDATE_GROOVY_SCRIPT_BODY", Map.of(
                "type", "VISIT_MANAGER_ACTION",
                "scriptBody", "return [oops: true"
        ), "tester");
        Assertions.assertEquals(false, invalid.get("ok"));
        Assertions.assertTrue(String.valueOf(invalid.get("message")).length() > 5);
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
