package ru.suo.integration.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.suo.integration.config.IntegrationGatewayConfiguration;

import java.util.List;
import java.util.Map;

class RoutingServiceTest {

    @Test
    void shouldResolveByBranchMapping() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-default");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-100", "vm-branch"));

        RoutingService service = new RoutingService(cfg);

        Assertions.assertEquals("vm-branch", service.resolveTarget("BR-100", ""));
    }

    @Test
    void shouldPreferExplicitTarget() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-default");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));

        RoutingService service = new RoutingService(cfg);

        Assertions.assertEquals("vm-explicit", service.resolveTarget("BR-100", "vm-explicit"));
    }

    @Test
    void shouldResolveConfiguredFallback() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm1 = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm1.setId("vm-1");
        vm1.setBaseUrl("http://localhost");
        vm1.setActive(true);
        IntegrationGatewayConfiguration.VisitManagerInstance vm2 = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm2.setId("vm-2");
        vm2.setBaseUrl("http://localhost");
        vm2.setActive(true);

        cfg.setVisitManagers(List.of(vm1, vm2));
        cfg.setBranchFallbackRouting(Map.of("BR-100", "vm-2"));

        RoutingService service = new RoutingService(cfg);

        Assertions.assertEquals("vm-2", service.resolveFallbackTarget("BR-100", "vm-1"));
    }
}
