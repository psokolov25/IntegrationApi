package ru.aritmos.integration.eventing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.audit.AuditService;
import ru.aritmos.integration.client.StubVisitManagerClient;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerBranchStateEventMapper;
import ru.aritmos.integration.service.BranchStateCache;
import ru.aritmos.integration.service.GatewayService;
import ru.aritmos.integration.service.QueueCache;
import ru.aritmos.integration.service.RoutingService;
import ru.aritmos.integration.service.VisitManagerMetricsService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class BranchStateUpdatedEventHandlerTest {

    @Test
    void shouldUpdateBranchStateCacheFromEvent() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-11", "vm-main"));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );
        BranchStateUpdatedEventHandler handler = new BranchStateUpdatedEventHandler(gatewayService, new VisitManagerBranchStateEventMapper());

        handler.handle(new IntegrationEvent(
                "evt-1",
                "branch-state-updated",
                "visit-manager",
                Instant.parse("2026-01-10T10:00:00Z"),
                Map.of(
                        "branchId", "BR-11",
                        "targetVisitManagerId", "vm-main",
                        "status", "CLOSED",
                        "activeWindow", "09:00-17:00",
                        "queueSize", 0,
                        "updatedBy", "system"
                )
        ));

        var state = gatewayService.getBranchState("subject", "BR-11", "");
        Assertions.assertTrue(state.cached());
        Assertions.assertEquals("CLOSED", state.status());
        Assertions.assertEquals("system", state.updatedBy());
    }

    @Test
    void shouldParseNestedDatabusPayloadAndUseSourceAsTargetFallback() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-12", "vm-main"));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );
        BranchStateUpdatedEventHandler handler = new BranchStateUpdatedEventHandler(gatewayService, new VisitManagerBranchStateEventMapper());

        handler.handle(new IntegrationEvent(
                "evt-2",
                "branch-state-updated",
                "vm-main",
                Instant.parse("2026-01-10T10:05:00Z"),
                Map.of(
                        "data", Map.of(
                                "branch", Map.of("id", "BR-12"),
                                "state", Map.of(
                                        "status", "PAUSED",
                                        "activeWindow", "10:00-19:00",
                                        "queueSize", 2,
                                        "updatedAt", "2026-01-10T10:04:59Z",
                                        "updatedBy", "operator-console"
                                )
                        )
                )
        ));

        var state = gatewayService.getBranchState("subject", "BR-12", "");
        Assertions.assertTrue(state.cached());
        Assertions.assertEquals("PAUSED", state.status());
        Assertions.assertEquals("operator-console", state.updatedBy());
        Assertions.assertEquals("vm-main", state.sourceVisitManagerId());
    }

    @Test
    void shouldIgnoreOutdatedEventState() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-13", "vm-main"));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );
        BranchStateUpdatedEventHandler handler = new BranchStateUpdatedEventHandler(gatewayService, new VisitManagerBranchStateEventMapper());

        handler.handle(new IntegrationEvent(
                "evt-3-new",
                "branch-state-updated",
                "visit-manager",
                Instant.parse("2026-01-10T10:10:00Z"),
                Map.of(
                        "branchId", "BR-13",
                        "targetVisitManagerId", "vm-main",
                        "status", "OPEN",
                        "activeWindow", "09:00-20:00",
                        "queueSize", 1,
                        "updatedAt", "2026-01-10T10:10:00Z",
                        "updatedBy", "system"
                )
        ));
        handler.handle(new IntegrationEvent(
                "evt-3-old",
                "branch-state-updated",
                "visit-manager",
                Instant.parse("2026-01-10T09:55:00Z"),
                Map.of(
                        "branchId", "BR-13",
                        "targetVisitManagerId", "vm-main",
                        "status", "CLOSED",
                        "activeWindow", "09:00-20:00",
                        "queueSize", 0,
                        "updatedAt", "2026-01-10T09:55:00Z",
                        "updatedBy", "legacy-job"
                )
        ));

        var state = gatewayService.getBranchState("subject", "BR-13", "");
        Assertions.assertEquals("OPEN", state.status());
        Assertions.assertEquals("system", state.updatedBy());
    }
}
