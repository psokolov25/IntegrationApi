package ru.aritmos.integration.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.audit.AuditService;
import ru.aritmos.integration.client.StubVisitManagerClient;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.domain.BranchStateUpdateRequest;

import java.util.List;
import java.util.Map;

class GatewayBranchStateTest {

    @Test
    void shouldUseBranchStateCacheAndAllowUpdate() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-7", "vm-main"));

        GatewayService service = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        BranchStateDto loaded = service.getBranchState("subject", "BR-7", "");
        Assertions.assertFalse(loaded.cached());

        BranchStateDto cached = service.getBranchState("subject", "BR-7", "");
        Assertions.assertTrue(cached.cached());

        BranchStateDto updated = service.updateBranchState(
                "subject",
                "BR-7",
                new BranchStateUpdateRequest("PAUSED", "09:00-18:00", 3, "reception-console"),
                ""
        );
        Assertions.assertEquals("PAUSED", updated.status());

        BranchStateDto afterUpdate = service.getBranchState("subject", "BR-7", "");
        Assertions.assertTrue(afterUpdate.cached());
        Assertions.assertEquals("PAUSED", afterUpdate.status());
    }

    @Test
    void shouldKeepBranchStateCacheIsolatedByVisitManagerTarget() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vmMain = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vmMain.setId("vm-main");
        vmMain.setBaseUrl("http://localhost");
        vmMain.setActive(true);
        IntegrationGatewayConfiguration.VisitManagerInstance vmBackup = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vmBackup.setId("vm-backup");
        vmBackup.setBaseUrl("http://localhost");
        vmBackup.setActive(true);
        cfg.setVisitManagers(List.of(vmMain, vmBackup));
        cfg.setBranchRouting(Map.of("BR-8", "vm-main"));

        GatewayService service = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        service.updateBranchState("subject", "BR-8", new BranchStateUpdateRequest("OPEN", "09:00-18:00", 1, "main-console"), "vm-main");
        service.updateBranchState("subject", "BR-8", new BranchStateUpdateRequest("PAUSED", "09:00-18:00", 5, "backup-console"), "vm-backup");

        BranchStateDto main = service.getBranchState("subject", "BR-8", "vm-main");
        BranchStateDto backup = service.getBranchState("subject", "BR-8", "vm-backup");

        Assertions.assertEquals("OPEN", main.status());
        Assertions.assertEquals("PAUSED", backup.status());
    }
}
