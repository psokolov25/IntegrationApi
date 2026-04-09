package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.audit.AuditService;
import ru.aritmos.integration.client.StubVisitManagerClient;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.security.core.AuthorizationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;
import ru.aritmos.integration.service.BranchStateCache;
import ru.aritmos.integration.service.GatewayService;
import ru.aritmos.integration.service.QueueCache;
import ru.aritmos.integration.service.RoutingService;
import ru.aritmos.integration.service.VisitManagerMetricsService;

import java.util.List;
import java.util.Map;
import java.util.Set;

class GroovyScriptServiceTest {

    @Test
    void shouldSaveAndExecuteBranchCacheScript() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-01", "vm-main"));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        GroovyScriptService service = new GroovyScriptService(
                new InMemoryGroovyScriptStorage(),
                cfg,
                gatewayService,
                new VisitManagerRestInvoker(cfg, new ObjectMapper()),
                new ExternalRestClient(cfg, new ObjectMapper()),
                new CustomerMessageBusGateway(cfg, List.of(new LoggingMessageBusAdapter())),
                new AuthorizationService(),
                new ObjectMapper()
        );

        SubjectPrincipal subject = new SubjectPrincipal("tech-user", Set.of("programmable-script-manage", "programmable-script-execute"));
        service.save(
                "branch-state-view",
                GroovyScriptType.BRANCH_CACHE_QUERY,
                "def s = getBranchState.apply(input.branchId as String, input.target as String); [status: s.status(), queue: s.queueSize()]",
                "Скрипт чтения состояния отделения",
                subject
        );

        Object result = service.execute("branch-state-view", new ObjectMapper().valueToTree(Map.of("branchId", "BR-01", "target", "")), subject);
        Assertions.assertInstanceOf(Map.class, result);
        Map<?, ?> resultMap = (Map<?, ?>) result;
        Assertions.assertEquals("OPEN", resultMap.get("status"));
        Assertions.assertEquals(0, resultMap.get("queue"));
    }

    @Test
    void shouldDenySaveWithoutManagePermission() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        GroovyScriptService service = new GroovyScriptService(
                new InMemoryGroovyScriptStorage(),
                cfg,
                gatewayService,
                new VisitManagerRestInvoker(cfg, new ObjectMapper()),
                new ExternalRestClient(cfg, new ObjectMapper()),
                new CustomerMessageBusGateway(cfg, List.of(new LoggingMessageBusAdapter())),
                new AuthorizationService(),
                new ObjectMapper()
        );

        SubjectPrincipal subject = new SubjectPrincipal("viewer", Set.of("programmable-script-execute"));
        Assertions.assertThrows(SecurityException.class, () -> service.save(
                "denied",
                GroovyScriptType.BRANCH_CACHE_QUERY,
                "return [:]",
                "",
                subject
        ));
    }

    @Test
    void shouldReactOnInboundBusMessageViaConfiguredRoute() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));

        IntegrationGatewayConfiguration.MessageReactionRouteSettings route = new IntegrationGatewayConfiguration.MessageReactionRouteSettings();
        route.setBrokerId("customer-databus");
        route.setTopic("branch.state.changed");
        route.setScriptId("bus-reaction-script");
        cfg.getProgrammableApi().setMessageReactions(List.of(route));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        GroovyScriptService service = new GroovyScriptService(
                new InMemoryGroovyScriptStorage(),
                cfg,
                gatewayService,
                new VisitManagerRestInvoker(cfg, new ObjectMapper()),
                new ExternalRestClient(cfg, new ObjectMapper()),
                new CustomerMessageBusGateway(cfg, List.of(new LoggingMessageBusAdapter())),
                new AuthorizationService(),
                new ObjectMapper()
        );

        SubjectPrincipal subject = new SubjectPrincipal("reactor", Set.of("programmable-script-manage", "programmable-script-execute"));
        service.save(
                "bus-reaction-script",
                GroovyScriptType.MESSAGE_BUS_REACTION,
                "return [topic: input.topic, branchId: input.payload.branchId, status: 'processed']",
                "Реакция на события шины",
                subject
        );

        var results = service.reactOnIncomingMessage(
                "customer-databus",
                "branch.state.changed",
                "BR-02",
                Map.of("branchId", "BR-02", "status", "OPEN"),
                Map.of("x-correlation-id", "corr-1"),
                "",
                subject
        );

        Assertions.assertEquals(1, results.size());
        Assertions.assertInstanceOf(Map.class, results.get(0));
        Map<?, ?> response = (Map<?, ?>) results.get(0);
        Assertions.assertEquals("branch.state.changed", response.get("topic"));
        Assertions.assertEquals("BR-02", response.get("branchId"));
        Assertions.assertEquals("processed", response.get("status"));
    }

    @Test
    void shouldPassExecutionParametersWithoutLossInAdvancedMode() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        GroovyScriptService service = new GroovyScriptService(
                new InMemoryGroovyScriptStorage(),
                cfg,
                gatewayService,
                new VisitManagerRestInvoker(cfg, new ObjectMapper()),
                new ExternalRestClient(cfg, new ObjectMapper()),
                new CustomerMessageBusGateway(cfg, List.of(new LoggingMessageBusAdapter())),
                new AuthorizationService(),
                new ObjectMapper()
        );

        SubjectPrincipal subject = new SubjectPrincipal("ops", Set.of("programmable-script-manage", "programmable-script-execute"));
        service.save(
                "advanced-params",
                GroovyScriptType.BRANCH_CACHE_QUERY,
                "return [payloadBranch: input.branchId, endpoint: params.endpoint, retries: parameters.retryCount, trace: context.traceId]",
                "Проверка 100% передачи параметров",
                subject
        );

        Object result = service.executeAdvanced(
                "advanced-params",
                new ObjectMapper().valueToTree(Map.of("branchId", "BR-07")),
                Map.of("endpoint", "https://example.local", "retryCount", 3),
                Map.of("traceId", "trace-42"),
                subject
        );

        Assertions.assertInstanceOf(Map.class, result);
        Map<?, ?> output = (Map<?, ?>) result;
        Assertions.assertEquals("BR-07", output.get("payloadBranch"));
        Assertions.assertEquals("https://example.local", output.get("endpoint"));
        Assertions.assertEquals(3, output.get("retries"));
        Assertions.assertEquals("trace-42", output.get("trace"));
    }
}
