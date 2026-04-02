package ru.aritmos.integration.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.audit.AuditService;
import ru.aritmos.integration.client.StubVisitManagerClient;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

class GatewayServiceCacheTest {

    @Test
    void shouldReturnCachedResponseOnSecondRequest() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.setQueueCacheTtl(Duration.ofMinutes(1));
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-1", "vm-main"));

        GatewayService service = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        Assertions.assertFalse(service.getQueues("subj", "BR-1", "").cached());
        Assertions.assertTrue(service.getQueues("subj", "BR-1", "").cached());
    }
}
