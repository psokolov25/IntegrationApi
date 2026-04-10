package ru.aritmos.integration.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.audit.AuditService;
import ru.aritmos.integration.client.StubVisitManagerClient;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.AggregatedQueuesResponse;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.domain.BranchStateUpdateRequest;
import ru.aritmos.integration.domain.CallVisitorRequest;
import ru.aritmos.integration.domain.CallVisitorResponse;
import ru.aritmos.integration.domain.QueueItemDto;

import java.time.Instant;
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
        Assertions.assertTrue(response.failed().get(0).message().contains("временно недоступен"));
    }


    @Test
    void shouldDeduplicateBranchIdsInAggregateRequest() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();

        IntegrationGatewayConfiguration.VisitManagerInstance vmMain = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vmMain.setId("vm-main");
        vmMain.setBaseUrl("http://vm-main");
        vmMain.setActive(true);

        cfg.setVisitManagers(List.of(vmMain));
        cfg.setBranchRouting(Map.of("BR-1", "vm-main", "BR-2", "vm-main"));

        GatewayService service = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        AggregatedQueuesResponse response = service.getAggregatedQueues("ext", java.util.Arrays.asList(" BR-1 ", "BR-1", "", null, "BR-2", "  BR-2  "));

        Assertions.assertFalse(response.partial());
        Assertions.assertEquals(2, response.successful().size());
        Assertions.assertEquals(List.of("BR-1", "BR-2"), response.successful().stream().map(item -> item.branchId()).toList());
    }


    @Test
    void shouldReturnPartialWhenAggregateTimeoutExceeded() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.setAggregateRequestTimeoutMillis(10);

        IntegrationGatewayConfiguration.VisitManagerInstance vmMain = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vmMain.setId("vm-main");
        vmMain.setBaseUrl("http://vm-main");
        vmMain.setActive(true);

        cfg.setVisitManagers(List.of(vmMain));
        cfg.setBranchRouting(Map.of("BR-TIMEOUT", "vm-main"));

        GatewayService service = new GatewayService(
                new RoutingService(cfg),
                new SlowVisitManagerClient(),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        AggregatedQueuesResponse response = service.getAggregatedQueues("ext", List.of("BR-TIMEOUT"));

        Assertions.assertTrue(response.partial());
        Assertions.assertEquals(0, response.successful().size());
        Assertions.assertEquals(1, response.failed().size());
        Assertions.assertTrue(response.failed().get(0).message().contains("timeout"));
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

    private static class SlowVisitManagerClient implements ru.aritmos.integration.client.VisitManagerClient {

        @Override
        public List<QueueItemDto> getQueues(String targetVisitManagerId, String branchId) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return List.of(new QueueItemDto(branchId + "-Q", "Основная", 1));
        }

        @Override
        public CallVisitorResponse callVisitor(String targetVisitManagerId, String visitorId, CallVisitorRequest request) {
            return new CallVisitorResponse(visitorId, "CALLED", targetVisitManagerId);
        }

        @Override
        public BranchStateDto getBranchState(String targetVisitManagerId, String branchId) {
            return new BranchStateDto(branchId, targetVisitManagerId, "OPEN", "08:00-20:00", 0, Instant.now(), false, "slow-client");
        }

        @Override
        public BranchStateDto updateBranchState(String targetVisitManagerId, String branchId, BranchStateUpdateRequest request) {
            return new BranchStateDto(branchId, targetVisitManagerId, request.status(), request.activeWindow(), request.queueSize(), Instant.now(), false, request.updatedBy());
        }
    }

}
