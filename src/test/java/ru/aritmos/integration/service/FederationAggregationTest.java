package ru.aritmos.integration.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.audit.AuditService;
import ru.aritmos.integration.client.StubVisitManagerClient;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.AggregatedQueuesResponse;

import java.util.List;
import java.util.Map;

class FederationAggregationTest {

    @Test
    void shouldReturnPartialResultWhenOneTargetUnavailable() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();

        IntegrationGatewayConfiguration.VisitManagerInstance vmCentral = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vmCentral.setId("vm-central");
        vmCentral.setBaseUrl("http://vm-central");
        vmCentral.setActive(true);

        IntegrationGatewayConfiguration.VisitManagerInstance vmNorth = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vmNorth.setId("vm-north");
        vmNorth.setBaseUrl("http://vm-north");
        vmNorth.setActive(true);

        cfg.setVisitManagers(List.of(vmCentral, vmNorth));
        cfg.setBranchRouting(Map.of("BR-1", "vm-central", "BR-2", "vm-north"));
        cfg.setSimulatedUnavailableTargets(List.of("vm-north"));

        GatewayService service = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        AggregatedQueuesResponse response = service.getAggregatedQueues("ext", List.of("BR-1", "BR-2"));

        Assertions.assertTrue(response.partial());
        Assertions.assertEquals(1, response.successful().size());
        Assertions.assertEquals(1, response.failed().size());
    }

    @Test
    void shouldUseFallbackWhenPrimaryIsUnavailable() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();

        IntegrationGatewayConfiguration.VisitManagerInstance vmPrimary = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vmPrimary.setId("vm-primary");
        vmPrimary.setBaseUrl("http://vm-primary");
        vmPrimary.setActive(true);

        IntegrationGatewayConfiguration.VisitManagerInstance vmBackup = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vmBackup.setId("vm-backup");
        vmBackup.setBaseUrl("http://vm-backup");
        vmBackup.setActive(true);

        cfg.setVisitManagers(List.of(vmPrimary, vmBackup));
        cfg.setBranchRouting(Map.of("BR-9", "vm-primary"));
        cfg.setBranchFallbackRouting(Map.of("BR-9", "vm-backup"));
        cfg.setSimulatedUnavailableTargets(List.of("vm-primary"));

        GatewayService service = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        var response = service.getQueues("ext", "BR-9", "");
        Assertions.assertEquals("vm-backup", response.sourceVisitManagerId());
    }
}
