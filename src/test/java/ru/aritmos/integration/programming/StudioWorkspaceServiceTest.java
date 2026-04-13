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
