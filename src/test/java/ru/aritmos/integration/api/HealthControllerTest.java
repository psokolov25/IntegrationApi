package ru.aritmos.integration.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.DefaultVisitCreatedEventHandler;
import ru.aritmos.integration.eventing.EventDispatcherService;
import ru.aritmos.integration.eventing.EventInboxService;
import ru.aritmos.integration.eventing.EventRetryService;
import ru.aritmos.integration.eventing.EventStoreService;
import ru.aritmos.integration.security.core.SecurityMode;
import ru.aritmos.integration.service.RuntimeHardwareProbe;
import ru.aritmos.integration.service.RuntimeSafetyLimitService;

import java.util.List;
import java.util.Map;

class HealthControllerTest {

    @Test
    void shouldReturnUpWhenEventingDisabled() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(false);
        cfg.setApiKeys(List.of("dev-key"));
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-01", "vm-main"));
        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );
        HealthController controller = new HealthController(cfg, dispatcher, new RuntimeSafetyLimitService(cfg, new RuntimeHardwareProbe()));

        var readiness = controller.readiness();
        Assertions.assertEquals("UP", readiness.status());
        Assertions.assertEquals("DISABLED", readiness.components().get("eventing"));
        Assertions.assertEquals("UP", readiness.components().get("security"));
        Assertions.assertEquals("UP", readiness.components().get("gateway"));
        Assertions.assertEquals("DISABLED", readiness.components().get("federation"));
        Assertions.assertEquals("UP", readiness.components().get("aggregation"));
        Assertions.assertEquals("DISABLED", readiness.components().get("programmable-api"));
        Assertions.assertEquals("ENABLED", readiness.components().get("client-policy"));
        Assertions.assertEquals("UP", readiness.components().get("runtime-safety"));
        Assertions.assertEquals("UP", readiness.components().get("observability"));
    }

    @Test
    void shouldReturnLivenessUp() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );
        HealthController controller = new HealthController(cfg, dispatcher, new RuntimeSafetyLimitService(cfg, new RuntimeHardwareProbe()));

        var liveness = controller.liveness();
        Assertions.assertEquals("UP", liveness.status());
    }

    @Test
    void shouldReturnDegradedWhenGatewayHasNoActiveVisitManagers() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(false);
        cfg.setApiKeys(List.of("dev-key"));
        cfg.getClientPolicy().setRetryAttempts(0);
        cfg.getClientPolicy().setTimeoutMillis(0);
        cfg.getProgrammableApi().setEnabled(true);

        IntegrationGatewayConfiguration.VisitManagerInstance vmMain = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vmMain.setId("vm-main");
        vmMain.setBaseUrl("http://localhost");
        vmMain.setActive(false);
        IntegrationGatewayConfiguration.VisitManagerInstance vmBackup = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vmBackup.setId("vm-backup");
        vmBackup.setBaseUrl("http://localhost");
        vmBackup.setActive(false);
        cfg.setVisitManagers(List.of(vmMain, vmBackup));

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );
        HealthController controller = new HealthController(cfg, dispatcher, new RuntimeSafetyLimitService(cfg, new RuntimeHardwareProbe()));

        var readiness = controller.readiness();
        Assertions.assertEquals("DEGRADED", readiness.status());
        Assertions.assertEquals("UP", readiness.components().get("security"));
        Assertions.assertEquals("DEGRADED", readiness.components().get("gateway"));
        Assertions.assertEquals("ENABLED", readiness.components().get("federation"));
        Assertions.assertEquals("ENABLED", readiness.components().get("programmable-api"));
        Assertions.assertEquals("DISABLED", readiness.components().get("client-policy"));
    }

    @Test
    void shouldReturnDegradedWhenHybridSecurityIsPartiallyConfigured() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(false);
        cfg.setSecurityMode(SecurityMode.HYBRID);
        cfg.getApiKeys().clear();
        cfg.getKeycloak().setIssuer("");
        cfg.getKeycloak().setAudience("");
        cfg.setVisitManagers(List.of());

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );
        HealthController controller = new HealthController(cfg, dispatcher, new RuntimeSafetyLimitService(cfg, new RuntimeHardwareProbe()));

        var readiness = controller.readiness();
        Assertions.assertEquals("DEGRADED", readiness.status());
        Assertions.assertEquals("DEGRADED", readiness.components().get("security"));
    }

    @Test
    void shouldReturnDegradedWhenAggregationConfigInvalid() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(false);
        cfg.setApiKeys(List.of("dev-key"));
        cfg.setAggregateMaxBranches(0);
        cfg.setAggregateRequestTimeoutMillis(0);

        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );
        HealthController controller = new HealthController(cfg, dispatcher, new RuntimeSafetyLimitService(cfg, new RuntimeHardwareProbe()));

        var readiness = controller.readiness();
        Assertions.assertEquals("DEGRADED", readiness.status());
        Assertions.assertEquals("DOWN", readiness.components().get("aggregation"));
    }

    @Test
    void shouldReturnDownWhenApiKeySecurityHasNoKeys() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(false);
        cfg.setSecurityMode(SecurityMode.API_KEY);
        cfg.getApiKeys().clear();
        cfg.setVisitManagers(List.of());

        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );
        HealthController controller = new HealthController(cfg, dispatcher, new RuntimeSafetyLimitService(cfg, new RuntimeHardwareProbe()));

        var readiness = controller.readiness();
        Assertions.assertEquals("DEGRADED", readiness.status());
        Assertions.assertEquals("DOWN", readiness.components().get("security"));
    }
}
