package ru.aritmos.integration.programming;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.eventing.EventInboxService;
import ru.aritmos.integration.eventing.EventOutboxService;
import ru.aritmos.integration.eventing.IntegrationEvent;
import ru.aritmos.integration.service.BranchStateCache;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class StudioWorkspaceServiceTest {

    @Test
    void shouldBuildWorkspaceSnapshotAcrossFeatureGroups() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getProgrammableApi().setEnabled(true);
        cfg.getAnonymousAccess().setEnabled(true);
        cfg.getProgrammableApi().getScriptStorage().getFile().setEnabled(true);
        cfg.getProgrammableApi().getScriptStorage().getFile().setPath("cache/program-scripts");
        cfg.getProgrammableApi().getScriptStorage().getRedis().setEnabled(false);

        IntegrationGatewayConfiguration.MessageBrokerSettings broker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        broker.setId("broker-1");
        broker.setType("KAFKA");
        broker.setEnabled(true);
        cfg.getProgrammableApi().setMessageBrokers(List.of(broker));

        IntegrationGatewayConfiguration.ExternalRestServiceSettings rest = new IntegrationGatewayConfiguration.ExternalRestServiceSettings();
        rest.setId("crm");
        rest.setBaseUrl("http://crm.local");
        cfg.getProgrammableApi().setExternalRestServices(List.of(rest));

        IntegrationGatewayConfiguration.MessageReactionRouteSettings reaction = new IntegrationGatewayConfiguration.MessageReactionRouteSettings();
        reaction.setBrokerId("broker-1");
        reaction.setTopic("events.customer");
        reaction.setScriptId("reaction-1");
        cfg.getProgrammableApi().setMessageReactions(List.of(reaction));

        InMemoryGroovyScriptStorage storage = new InMemoryGroovyScriptStorage();
        storage.save(new StoredGroovyScript(
                "reaction-1",
                GroovyScriptType.MESSAGE_BUS_REACTION,
                "return [ok:true]",
                "reaction",
                Instant.parse("2026-02-01T10:00:00Z"),
                "qa"
        ));

        EventInboxService inbox = new EventInboxService();
        inbox.beginProcessing("evt-1");

        EventOutboxService outbox = new EventOutboxService();
        outbox.stage(new IntegrationEvent(
                "evt-2",
                "visit-created",
                "databus",
                Instant.parse("2026-02-01T10:05:00Z"),
                Map.of("visitId", "V-1")
        ));

        ScriptDebugHistoryService debugHistory = new ScriptDebugHistoryService();
        debugHistory.record(new ScriptDebugHistoryService.DebugEntry(
                "reaction-1",
                Instant.parse("2026-02-01T10:10:00Z"),
                35,
                true,
                Map.of("ok", true),
                null,
                Map.of("visitId", "V-1"),
                Map.of(),
                Map.of()
        ));

        StudioWorkspaceService service = new StudioWorkspaceService(
                cfg,
                inbox,
                outbox,
                storage,
                debugHistory,
                List.of(new KafkaOnlyBusAdapter())
        );

        Map<String, Object> snapshot = service.buildWorkspaceSnapshot(10);
        Map<String, Object> eventing = cast(snapshot.get("eventing"));
        Map<String, Object> connectors = cast(snapshot.get("connectors"));
        Map<String, Object> ide = cast(snapshot.get("ide"));
        Map<String, Object> runtime = cast(snapshot.get("runtime"));
        Map<String, Object> settings = cast(snapshot.get("settings"));
        Map<String, Object> gui = cast(snapshot.get("gui"));

        Assertions.assertEquals(1, cast(eventing.get("inbox")).get("size"));
        Assertions.assertEquals(1, cast(eventing.get("outbox")).get("pending"));
        Assertions.assertEquals(1, ((List<?>) cast(eventing.get("inbox")).get("recent")).size());
        Assertions.assertEquals(1, ((List<?>) cast(eventing.get("outbox")).get("recent")).size());
        Assertions.assertEquals(List.of("KAFKA"), connectors.get("supportedBrokerTypes"));
        Assertions.assertEquals(List.of(), connectors.get("unsupportedBrokerTypes"));
        Assertions.assertEquals(1, ide.get("debugHistorySize"));
        Assertions.assertEquals(1, ((List<?>) ide.get("debugHistoryRecent")).size());
        Assertions.assertEquals(1, runtime.get("scriptCount"));
        Assertions.assertTrue(cast(runtime.get("httpProcessing")).containsKey("enabled"));
        Assertions.assertTrue(settings.containsKey("httpProcessingEnabled"));
        Assertions.assertEquals(List.of("В inbox есть PROCESSING-события: 1"), gui.get("warnings"));
        List<Map<String, Object>> actions = castListOfMaps(gui.get("actions"));
        Assertions.assertFalse(actions.isEmpty());
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.http.processing.profile.export".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.http.processing.profile.preview".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.http.processing.profile.apply".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.http.processing.preview".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.http.processing.matrix".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.connector.profile.preview".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.connector.profile.validate".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.openapi.clients.generate".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.openapi.clients.apply".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.connector.presets.export".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.connector.presets.import.preview".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.connector.presets.import.diff".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.connector.presets.import.apply".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.integration.bundle.export".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.integration.bundle.preview".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "studio.integration.bundle.apply".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "connectors.crm.identify-client".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "connectors.crm.medical-services".equals(action.get("id"))));
        Assertions.assertTrue(actions.stream()
                .anyMatch(action -> "connectors.crm.prebooking".equals(action.get("id"))));
    }

    @Test
    void shouldReportUnsupportedBrokerTypes() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.MessageBrokerSettings broker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        broker.setId("broker-unsupported");
        broker.setType("AMQP_CUSTOM");
        broker.setEnabled(true);
        cfg.getProgrammableApi().setMessageBrokers(List.of(broker));

        StudioWorkspaceService service = new StudioWorkspaceService(
                cfg,
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        Map<String, Object> snapshot = service.buildWorkspaceSnapshot(5);
        Map<String, Object> connectors = cast(snapshot.get("connectors"));
        Map<String, Object> gui = cast(snapshot.get("gui"));
        Assertions.assertEquals(List.of("AMQP_CUSTOM"), connectors.get("unsupportedBrokerTypes"));
        Assertions.assertEquals(List.of("Обнаружены неподдерживаемые типы брокеров: AMQP_CUSTOM"), gui.get("warnings"));
    }

    @Test
    void shouldProvidePlaybookForAllStudioFeatureGroups() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        List<Map<String, Object>> playbook = service.buildPlaybook();
        Assertions.assertEquals(15, playbook.size());
        Assertions.assertEquals("connectors-health", playbook.get(0).get("group"));
        Assertions.assertEquals("visit-manager-routing", playbook.get(1).get("group"));
        Assertions.assertEquals("gui-ops", playbook.get(playbook.size() - 1).get("group"));
        Assertions.assertEquals("HIGH", playbook.get(0).get("importance"));
    }

    @Test
    void shouldSupportPlaybookSortingByOriginalOrder() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        List<Map<String, Object>> ordered = service.buildPlaybook("order");
        Assertions.assertEquals(15, ordered.size());
        Assertions.assertEquals("connectors-health", ordered.get(0).get("group"));
        Assertions.assertEquals("message-bus-smoke", ordered.get(5).get("group"));
        Assertions.assertEquals("gui-ops", ordered.get(14).get("group"));
    }

    @Test
    void shouldRejectUnsupportedPlaybookSortMode() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.buildPlaybook("priority")
        );
        Assertions.assertEquals("sortBy поддерживает только значения: importance, order, group", error.getMessage());
    }

    @Test
    void shouldSupportPlaybookSortingByGroup() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        List<Map<String, Object>> grouped = service.buildPlaybook("group");
        Assertions.assertEquals(15, grouped.size());
        Assertions.assertEquals("branch-cache", grouped.get(0).get("group"));
        Assertions.assertEquals("branch-state-sync", grouped.get(1).get("group"));
        Assertions.assertEquals("visit-manager-routing", grouped.get(grouped.size() - 1).get("group"));
    }

    @Test
    void shouldSupportDescendingPlaybookSortOrder() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        List<Map<String, Object>> descending = service.buildPlaybook("order", null, null, "desc");
        Assertions.assertEquals(15, descending.size());
        Assertions.assertEquals("gui-ops", descending.get(0).get("group"));
        Assertions.assertEquals("connectors-health", descending.get(descending.size() - 1).get("group"));
    }

    @Test
    void shouldRejectUnsupportedPlaybookSortOrder() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.buildPlaybook("order", null, null, "down")
        );
        Assertions.assertEquals("sortOrder поддерживает только значения: asc, desc", error.getMessage());
    }

    @Test
    void shouldFilterPlaybookByImportanceAndGroup() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        List<Map<String, Object>> filtered = service.buildPlaybook("importance", "HIGH", "branch-state-sync");
        Assertions.assertEquals(1, filtered.size());
        Assertions.assertEquals("HIGH", filtered.get(0).get("importance"));
        Assertions.assertEquals("branch-state-sync", filtered.get(0).get("group"));
    }

    @Test
    void shouldRejectUnsupportedPlaybookImportanceFilter() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.buildPlaybook("importance", "CRITICAL", null)
        );
        Assertions.assertEquals("importance поддерживает только значения: HIGH, MEDIUM, LOW (одно или несколько через запятую)", error.getMessage());
    }

    @Test
    void shouldSupportMultiValueFiltersForPlaybook() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        List<Map<String, Object>> filtered = service.buildPlaybook(
                "order",
                "HIGH, MEDIUM",
                "branch-state-sync, runtime-settings"
        );
        Assertions.assertEquals(2, filtered.size());
        Assertions.assertEquals("branch-state-sync", filtered.get(0).get("group"));
        Assertions.assertEquals("runtime-settings", filtered.get(1).get("group"));
    }

    @Test
    void shouldRejectUnsupportedPlaybookGroupFilter() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.buildPlaybook("importance", null, "unknown-group")
        );
        Assertions.assertTrue(error.getMessage().contains("group содержит неподдерживаемые значения: unknown-group"));
        Assertions.assertTrue(error.getMessage().contains("Поддерживаемые группы: connectors-health"));
    }

    @Test
    void shouldExposePlaybookOptionsForGui() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        Map<String, Object> options = service.playbookOptions();
        Assertions.assertEquals(List.of("importance", "order", "group"), options.get("sortBy"));
        Assertions.assertEquals(List.of("asc", "desc"), options.get("sortOrder"));
        Assertions.assertEquals(List.of("HIGH", "MEDIUM", "LOW"), options.get("importance"));
        @SuppressWarnings("unchecked")
        List<String> groups = (List<String>) options.get("groups");
        Assertions.assertEquals(15, groups.size());
        Assertions.assertEquals("branch-cache", groups.get(0));
        @SuppressWarnings("unchecked")
        Map<String, Object> limit = (Map<String, Object>) options.get("limit");
        Assertions.assertEquals(50, limit.get("default"));
        Assertions.assertEquals(200, limit.get("max"));
        @SuppressWarnings("unchecked")
        Map<String, Object> offset = (Map<String, Object>) options.get("offset");
        Assertions.assertEquals(0, offset.get("default"));
        Assertions.assertEquals(10000, offset.get("max"));
    }

    @Test
    void shouldFilterPlaybookByQuery() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        List<Map<String, Object>> result = service.buildPlaybook("importance", null, null, "asc", "runtime");
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.stream().allMatch(item ->
                String.valueOf(item.get("group")).contains("runtime")
                        || String.valueOf(item.get("title")).toLowerCase().contains("runtime")
                        || String.valueOf(item.get("check")).toLowerCase().contains("runtime")
                        || String.valueOf(item.get("api")).toLowerCase().contains("runtime")));
    }

    @Test
    void shouldApplyPlaybookLimit() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        List<Map<String, Object>> result = service.buildPlaybook("order", null, null, "asc", null, 2);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("connectors-health", result.get(0).get("group"));
        Assertions.assertEquals("visit-manager-routing", result.get(1).get("group"));
    }

    @Test
    void shouldRejectUnsupportedPlaybookLimit() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.buildPlaybook("order", null, null, "asc", null, 0)
        );
        Assertions.assertEquals("limit поддерживает только значения в диапазоне 1..200", error.getMessage());
    }

    @Test
    void shouldApplyPlaybookOffset() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        List<Map<String, Object>> result = service.buildPlaybook("order", null, null, "asc", null, 3, 2);
        Assertions.assertEquals(3, result.size());
        Assertions.assertEquals("queue-smoke", result.get(0).get("group"));
    }

    @Test
    void shouldRejectUnsupportedPlaybookOffset() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.buildPlaybook("order", null, null, "asc", null, 5, -1)
        );
        Assertions.assertEquals("offset поддерживает только значения в диапазоне 0..10000", error.getMessage());
    }

    @Test
    void shouldBuildPlaybookPageWithMetadata() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        Map<String, Object> page = service.buildPlaybookPage("order", null, null, "asc", null, 4, 10);
        Assertions.assertEquals(15, page.get("total"));
        Assertions.assertEquals(4, page.get("limit"));
        Assertions.assertEquals(10, page.get("offset"));
        Assertions.assertEquals(true, page.get("hasMore"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        Assertions.assertEquals(4, items.size());
        Assertions.assertEquals("settings", items.get(1).get("group"));
    }

    @Test
    void shouldBuildPlaybookPageWhenOffsetOutOfRange() {
        StudioWorkspaceService service = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter())
        );

        Map<String, Object> page = service.buildPlaybookPage("order", null, null, "asc", null, 5, 30);
        Assertions.assertEquals(15, page.get("total"));
        Assertions.assertEquals(5, page.get("limit"));
        Assertions.assertEquals(30, page.get("offset"));
        Assertions.assertEquals(false, page.get("hasMore"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        Assertions.assertTrue(items.isEmpty());
    }

    @Test
    void shouldBuildDashboardSnapshotForGui() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost:8081");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));

        IntegrationGatewayConfiguration.ExternalRestServiceSettings rest = new IntegrationGatewayConfiguration.ExternalRestServiceSettings();
        rest.setId("crm");
        rest.setBaseUrl("http://crm.local");
        cfg.getProgrammableApi().setExternalRestServices(List.of(rest));
        BranchStateCache branchStateCache = new BranchStateCache(cfg);
        branchStateCache.put(new BranchStateDto(
                "BR-1",
                "vm-main",
                "OPEN",
                "09:00-18:00",
                3,
                Instant.parse("2026-04-01T10:00:00Z"),
                false,
                "system:eventing"
        ));

        StudioWorkspaceService service = new StudioWorkspaceService(
                cfg,
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter()),
                branchStateCache
        );

        Map<String, Object> dashboard = service.buildDashboardSnapshot(25);
        Assertions.assertTrue(dashboard.containsKey("workspace"));
        Assertions.assertTrue(dashboard.containsKey("inboxOutbox"));
        Assertions.assertTrue(dashboard.containsKey("visitManagers"));
        Assertions.assertTrue(dashboard.containsKey("branchStateCache"));
        Assertions.assertTrue(dashboard.containsKey("externalServices"));
        Assertions.assertTrue(dashboard.containsKey("runtimeSettings"));

        Map<String, Object> visitManagers = cast(dashboard.get("visitManagers"));
        Assertions.assertEquals(1, visitManagers.get("visitManagersCount"));
        Map<String, Object> external = cast(dashboard.get("externalServices"));
        Assertions.assertEquals(1, external.get("restServicesCount"));
        Assertions.assertTrue(external.containsKey("supportedBrokerProfiles"));
        Map<String, Object> branchState = cast(dashboard.get("branchStateCache"));
        Assertions.assertEquals(1, branchState.get("total"));
        Map<String, Object> runtimeSettings = cast(dashboard.get("runtimeSettings"));
        Assertions.assertEquals(cfg.getAggregateMaxBranches(), runtimeSettings.get("aggregateMaxBranches"));
        Assertions.assertTrue(runtimeSettings.containsKey("httpProcessing"));
    }

    @Test
    void shouldSortBranchStateRecentByUpdatedAtDesc() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        BranchStateCache branchStateCache = new BranchStateCache(cfg);
        branchStateCache.put(new BranchStateDto(
                "BR-OLD",
                "vm-main",
                "OPEN",
                "09:00-18:00",
                1,
                Instant.parse("2026-04-01T10:00:00Z"),
                false,
                "system:eventing"
        ));
        branchStateCache.put(new BranchStateDto(
                "BR-NEW",
                "vm-main",
                "PAUSED",
                "09:00-18:00",
                2,
                Instant.parse("2026-04-01T12:00:00Z"),
                false,
                "system:eventing"
        ));
        branchStateCache.put(new BranchStateDto(
                "BR-NEW",
                "vm-backup",
                "OPEN",
                "09:00-18:00",
                3,
                Instant.parse("2026-04-01T12:00:00Z"),
                false,
                "system:eventing"
        ));
        branchStateCache.put(new BranchStateDto(
                "BR-NO-TIME",
                "vm-main",
                "UNKNOWN",
                "09:00-18:00",
                0,
                null,
                false,
                "system:eventing"
        ));

        StudioWorkspaceService service = new StudioWorkspaceService(
                cfg,
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of(new KafkaOnlyBusAdapter()),
                branchStateCache
        );

        Map<String, Object> snapshot = service.buildBranchStateCacheSnapshot(10);
        List<Map<String, Object>> recent = castListOfMaps(snapshot.get("recent"));

        Assertions.assertEquals(4, recent.size());
        Assertions.assertEquals("BR-NEW", recent.get(0).get("branchId"));
        Assertions.assertEquals("vm-backup", recent.get(0).get("visitManagerId"));
        Assertions.assertEquals("BR-NEW", recent.get(1).get("branchId"));
        Assertions.assertEquals("vm-main", recent.get(1).get("visitManagerId"));
        Assertions.assertEquals("BR-OLD", recent.get(2).get("branchId"));
        Assertions.assertEquals("BR-NO-TIME", recent.get(3).get("branchId"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castListOfMaps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static class KafkaOnlyBusAdapter implements CustomerMessageBusAdapter {
        @Override
        public boolean supports(String brokerType) {
            return "KAFKA".equalsIgnoreCase(brokerType);
        }

        @Override
        public List<String> supportedBrokerTypes() {
            return List.of("KAFKA");
        }

        @Override
        public List<Map<String, Object>> supportedBrokerProfiles() {
            return List.of(Map.of(
                    "type", "KAFKA",
                    "description", "Kafka test adapter",
                    "adapterMode", "TEST"
            ));
        }

        @Override
        public Map<String, Object> publish(IntegrationGatewayConfiguration.MessageBrokerSettings broker,
                                           BrokerMessageRequest message) {
            return Map.of("ok", true);
        }
    }
}
