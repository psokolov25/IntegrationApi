package ru.aritmos.integration.eventing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.audit.AuditService;
import ru.aritmos.integration.client.StubVisitManagerClient;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateUpdateRequest;
import ru.aritmos.integration.eventing.visitmanager.VisitManagerBranchStateEventMapper;
import ru.aritmos.integration.service.BranchStateCache;
import ru.aritmos.integration.service.GatewayService;
import ru.aritmos.integration.service.QueueCache;
import ru.aritmos.integration.service.RoutingService;
import ru.aritmos.integration.service.VisitManagerMetricsService;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class VisitManagerVisitEventHandlerTest {

    @Test
    void shouldRefreshBranchStateCacheOnVisitLifecycleEvent() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-501", "vm-main"));
        cfg.setBranchStateEventRefreshDebounce(Duration.ofMillis(500));

        StubVisitManagerClient client = new StubVisitManagerClient(cfg);
        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                client,
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );
        VisitManagerVisitEventHandler handler = new VisitManagerVisitEventHandler(
                gatewayService,
                new VisitManagerBranchStateEventMapper(),
                cfg,
                Clock.fixed(Instant.parse("2026-02-01T13:00:00Z"), ZoneOffset.UTC)
        );

        gatewayService.updateBranchState("subject", "BR-501",
                new BranchStateUpdateRequest("PAUSED", "09:00-18:00", 2, "operator"), "vm-main");
        var cachedBefore = gatewayService.getBranchState("subject", "BR-501", "vm-main");
        Assertions.assertEquals("PAUSED", cachedBefore.status());

        client.updateBranchState("vm-main", "BR-501",
                new BranchStateUpdateRequest("OPEN", "09:00-18:00", 1, "visit-manager"));
        handler.handle(new IntegrationEvent(
                "evt-visit-501",
                "VISIT_CALLED",
                "vm-main",
                Instant.parse("2026-02-01T13:00:00Z"),
                Map.of("visit", Map.of("branchId", "BR-501"))
        ));

        var refreshed = gatewayService.getBranchState("subject", "BR-501", "vm-main");
        Assertions.assertEquals("OPEN", refreshed.status());
        Assertions.assertEquals("visit-manager", refreshed.updatedBy());
    }

    @Test
    void shouldSkipRefreshInsideDebounceWindow() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-502", "vm-main"));
        cfg.setBranchStateEventRefreshDebounce(Duration.ofSeconds(10));

        StubVisitManagerClient client = new StubVisitManagerClient(cfg);
        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                client,
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );
        VisitManagerVisitEventHandler handler = new VisitManagerVisitEventHandler(
                gatewayService,
                new VisitManagerBranchStateEventMapper(),
                cfg,
                Clock.fixed(Instant.parse("2026-02-01T14:00:00Z"), ZoneOffset.UTC)
        );

        client.updateBranchState("vm-main", "BR-502",
                new BranchStateUpdateRequest("PAUSED", "09:00-18:00", 2, "vm-first"));
        handler.handle(new IntegrationEvent(
                "evt-visit-502-a",
                "VISIT_CALLED",
                "vm-main",
                Instant.parse("2026-02-01T14:00:00Z"),
                Map.of("visit", Map.of("branchId", "BR-502"))
        ));

        client.updateBranchState("vm-main", "BR-502",
                new BranchStateUpdateRequest("OPEN", "09:00-18:00", 1, "vm-second"));
        handler.handle(new IntegrationEvent(
                "evt-visit-502-b",
                "VISIT_CALLED",
                "vm-main",
                Instant.parse("2026-02-01T14:00:01Z"),
                Map.of("visit", Map.of("branchId", "BR-502"))
        ));

        var state = gatewayService.getBranchState("subject", "BR-502", "vm-main");
        Assertions.assertEquals("PAUSED", state.status(), "второй refresh должен быть пропущен в debounce-окне");
    }
}
