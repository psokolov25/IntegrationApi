package ru.aritmos.integration.programming;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.EventInboxService;
import ru.aritmos.integration.eventing.EventOutboxService;
import ru.aritmos.integration.eventing.IntegrationEvent;

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
        Assertions.assertEquals(List.of("В inbox есть PROCESSING-события: 1"), gui.get("warnings"));
        Assertions.assertFalse(((List<?>) gui.get("actions")).isEmpty());
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
        Assertions.assertEquals(6, playbook.size());
        Assertions.assertEquals("inbox-outbox", playbook.get(0).get("group"));
        Assertions.assertEquals("gui-ops", playbook.get(playbook.size() - 1).get("group"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
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
        public Map<String, Object> publish(IntegrationGatewayConfiguration.MessageBrokerSettings broker,
                                           BrokerMessageRequest message) {
            return Map.of("ok", true);
        }
    }
}
