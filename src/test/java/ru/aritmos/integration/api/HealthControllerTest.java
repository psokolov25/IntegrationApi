package ru.aritmos.integration.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
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
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

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
        Assertions.assertEquals("UP", readiness.components().get("visit-manager-client"));
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
    void shouldReturnDownWhenActiveVisitManagerHasNoBaseUrl() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(false);
        cfg.setApiKeys(List.of("dev-key"));
        cfg.getVisitManagerClient().setMode("HTTP");

        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl(" ");
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
        Assertions.assertEquals("DOWN", readiness.components().get("gateway"));
        Assertions.assertEquals("DOWN", readiness.components().get("visit-manager-client"));
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

    @Test
    void shouldReturnDownWhenVisitManagerClientIsStub() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(false);
        cfg.setApiKeys(List.of("dev-key"));
        cfg.getVisitManagerClient().setMode("STUB");
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
        Assertions.assertEquals("DOWN", readiness.components().get("visit-manager-client"));
    }

    @Test
    void shouldReturnDegradedWhenVisitManagerClientTemplatesInvalid() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(false);
        cfg.setApiKeys(List.of("dev-key"));
        cfg.getVisitManagerClient().setMode("HTTP");
        cfg.getVisitManagerClient().setCallPathTemplate("/api/v1/queues/call"); // без placeholders
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
        Assertions.assertEquals("DOWN", readiness.components().get("visit-manager-client"));
    }

    @Test
    void shouldReturnDegradedWhenVisitManagerProbeFails() throws Exception {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(false);
        cfg.setApiKeys(List.of("dev-key"));
        cfg.getVisitManagerClient().setMode("HTTP");
        cfg.getVisitManagerClient().setReadinessProbeEnabled(true);
        cfg.getVisitManagerClient().setReadinessProbePath("/health/readiness");
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://127.0.0.1:1"); // гарантированно недоступный порт
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
        Assertions.assertEquals("DEGRADED", readiness.components().get("visit-manager-client"));
    }

    @Test
    void shouldReturnUpWhenVisitManagerProbeSucceeds() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health/readiness", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
            cfg.getEventing().setEnabled(false);
            cfg.setApiKeys(List.of("dev-key"));
            cfg.getVisitManagerClient().setMode("HTTP");
            cfg.getVisitManagerClient().setReadinessProbeEnabled(true);
            cfg.getVisitManagerClient().setReadinessProbePath("/health/readiness");
            IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
            vm.setId("vm-main");
            vm.setBaseUrl("http://localhost:" + server.getAddress().getPort());
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
            Assertions.assertEquals("UP", readiness.status());
            Assertions.assertEquals("UP", readiness.components().get("visit-manager-client"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldSendConfiguredAuthHeaderForVisitManagerReadinessProbe() throws Exception {
        String expectedToken = "Bearer readiness-token";
        String expectedHeader = "X-VM-Auth";
        AtomicBoolean authSeen = new AtomicBoolean(false);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health/readiness", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst(expectedHeader);
            if (expectedToken.equals(auth)) {
                authSeen.set(true);
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(401, -1);
            }
            exchange.close();
        });
        server.start();
        try {
            IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
            cfg.getEventing().setEnabled(false);
            cfg.setApiKeys(List.of("dev-key"));
            cfg.getVisitManagerClient().setMode("HTTP");
            cfg.getVisitManagerClient().setReadinessProbeEnabled(true);
            cfg.getVisitManagerClient().setReadinessProbePath("health/readiness");
            cfg.getVisitManagerClient().setAuthHeader(expectedHeader);
            cfg.getVisitManagerClient().setAuthToken(expectedToken);
            IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
            vm.setId("vm-main");
            vm.setBaseUrl("http://localhost:" + server.getAddress().getPort());
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
            Assertions.assertEquals("UP", readiness.status());
            Assertions.assertEquals("UP", readiness.components().get("visit-manager-client"));
            Assertions.assertTrue(authSeen.get(), "Readiness probe должен передавать настроенный auth-header");
        } finally {
            server.stop(0);
        }
    }
}
