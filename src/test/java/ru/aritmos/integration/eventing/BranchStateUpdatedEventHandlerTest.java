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
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.Duration;
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
        BranchStateUpdatedEventHandler handler = new BranchStateUpdatedEventHandler(
                gatewayService, new VisitManagerBranchStateEventMapper(), cfg);

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
        BranchStateUpdatedEventHandler handler = new BranchStateUpdatedEventHandler(
                gatewayService, new VisitManagerBranchStateEventMapper(), cfg);

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
        BranchStateUpdatedEventHandler handler = new BranchStateUpdatedEventHandler(
                gatewayService, new VisitManagerBranchStateEventMapper(), cfg);

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

    @Test
    void shouldUpdateCacheFromEntityChangedBranchEvent() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-21", "vm-main"));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );
        BranchStateUpdatedEventHandler handler = new BranchStateUpdatedEventHandler(
                gatewayService, new VisitManagerBranchStateEventMapper(cfg), cfg);

        handler.handle(new IntegrationEvent(
                "evt-4",
                "ENTITY_CHANGED",
                "vm-main",
                Instant.parse("2026-01-10T10:15:00Z"),
                Map.of(
                        "meta", Map.of("visitManagerId", "vm-main"),
                        "data", Map.of(
                                "class", "Branch",
                                "entity", Map.of(
                                        "id", "BR-21",
                                        "status", "PAUSED",
                                        "activeWindow", "11:00-20:00",
                                        "queueSize", 5,
                                        "updatedAt", "2026-01-10T10:14:59Z",
                                        "updatedBy", "vm-sync"
                                )
                        )
                )
        ));

        var state = gatewayService.getBranchState("subject", "BR-21", "");
        Assertions.assertEquals("PAUSED", state.status());
        Assertions.assertEquals("vm-sync", state.updatedBy());
        Assertions.assertEquals(5, state.queueSize());
    }

    @Test
    void shouldIgnoreEntityChangedForNonBranchClass() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-22", "vm-main"));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );
        BranchStateUpdatedEventHandler handler = new BranchStateUpdatedEventHandler(
                gatewayService, new VisitManagerBranchStateEventMapper(cfg), cfg);

        handler.handle(new IntegrationEvent(
                "evt-5",
                "ENTITY_CHANGED",
                "vm-main",
                Instant.parse("2026-01-10T10:20:00Z"),
                Map.of(
                        "meta", Map.of("visitManagerId", "vm-main"),
                        "data", Map.of(
                                "class", "Visitor",
                                "entity", Map.of(
                                        "id", "V-1"
                                )
                        )
                )
        ));

        var state = gatewayService.getBranchState("subject", "BR-22", "");
        Assertions.assertEquals("OPEN", state.status(), "некорректный ENTITY_CHANGED не должен ломать branch-state");
        Assertions.assertEquals(0, state.queueSize());
    }

    @Test
    void shouldIgnoreDuplicateBranchStateEventByPayloadEventIdEvenIfEnvelopeIdDiffers() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.setBranchStateEventRefreshDebounce(Duration.ofSeconds(10));
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-23", "vm-main"));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );
        MutableClock clock = new MutableClock(Instant.parse("2026-01-10T10:30:00Z"));
        BranchStateUpdatedEventHandler handler = new BranchStateUpdatedEventHandler(
                gatewayService, new VisitManagerBranchStateEventMapper(cfg), cfg, clock);

        handler.handle(new IntegrationEvent(
                "evt-envelope-a",
                "branch-state-updated",
                "vm-main",
                Instant.parse("2026-01-10T10:30:00Z"),
                Map.of(
                        "eventId", "evt-dup-business",
                        "branchId", "BR-23",
                        "targetVisitManagerId", "vm-main",
                        "status", "PAUSED",
                        "activeWindow", "09:00-20:00",
                        "queueSize", 3,
                        "updatedAt", "2026-01-10T10:30:00Z",
                        "updatedBy", "sync-a"
                )
        ));

        clock.setCurrent(Instant.parse("2026-01-10T10:30:02Z"));
        handler.handle(new IntegrationEvent(
                "evt-envelope-b",
                "branch-state-updated",
                "vm-main",
                Instant.parse("2026-01-10T10:30:02Z"),
                Map.of(
                        "eventId", "evt-dup-business",
                        "branchId", "BR-23",
                        "targetVisitManagerId", "vm-main",
                        "status", "OPEN",
                        "activeWindow", "09:00-20:00",
                        "queueSize", 1,
                        "updatedAt", "2026-01-10T10:30:01Z",
                        "updatedBy", "sync-b"
                )
        ));

        var state = gatewayService.getBranchState("subject", "BR-23", "");
        Assertions.assertEquals("PAUSED", state.status(), "дубликат payload.eventId должен игнорироваться");
        Assertions.assertEquals("sync-a", state.updatedBy());
    }

    @Test
    void shouldApplySameTimestampEventWhenPayloadChangedInsideDebounceWindow() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.setBranchStateEventRefreshDebounce(Duration.ofSeconds(10));
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-24", "vm-main"));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );
        MutableClock clock = new MutableClock(Instant.parse("2026-01-10T10:40:00Z"));
        BranchStateUpdatedEventHandler handler = new BranchStateUpdatedEventHandler(
                gatewayService, new VisitManagerBranchStateEventMapper(cfg), cfg, clock);

        handler.handle(new IntegrationEvent(
                "evt-24-a",
                "branch-state-updated",
                "vm-main",
                Instant.parse("2026-01-10T10:40:00Z"),
                Map.of(
                        "eventId", "evt-24-a",
                        "branchId", "BR-24",
                        "targetVisitManagerId", "vm-main",
                        "status", "OPEN",
                        "activeWindow", "09:00-20:00",
                        "queueSize", 2,
                        "updatedAt", "2026-01-10T10:40:00Z",
                        "updatedBy", "sync-a"
                )
        ));

        clock.setCurrent(Instant.parse("2026-01-10T10:40:03Z"));
        handler.handle(new IntegrationEvent(
                "evt-24-b",
                "branch-state-updated",
                "vm-main",
                Instant.parse("2026-01-10T10:40:03Z"),
                Map.of(
                        "eventId", "evt-24-b",
                        "branchId", "BR-24",
                        "targetVisitManagerId", "vm-main",
                        "status", "PAUSED",
                        "activeWindow", "09:00-20:00",
                        "queueSize", 6,
                        "updatedAt", "2026-01-10T10:40:00Z",
                        "updatedBy", "sync-b"
                )
        ));

        var state = gatewayService.getBranchState("subject", "BR-24", "");
        Assertions.assertEquals("PAUSED", state.status(), "измененный payload должен применяться даже при том же updatedAt");
        Assertions.assertEquals(6, state.queueSize());
        Assertions.assertEquals("sync-b", state.updatedBy());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void setCurrent(Instant current) {
            this.current = current;
        }
    }
}
