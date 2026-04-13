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
import java.time.ZoneId;
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

    @Test
    void shouldIgnoreOutOfOrderVisitEventAfterNewerProcessedEvent() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-503", "vm-main"));
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
        MutableClock clock = new MutableClock(Instant.parse("2026-02-01T15:00:00Z"));
        VisitManagerVisitEventHandler handler = new VisitManagerVisitEventHandler(
                gatewayService,
                new VisitManagerBranchStateEventMapper(),
                cfg,
                clock
        );

        client.updateBranchState("vm-main", "BR-503",
                new BranchStateUpdateRequest("OPEN", "09:00-18:00", 3, "vm-new"));
        handler.handle(new IntegrationEvent(
                "evt-visit-503-new",
                "VISIT_CALLED",
                "vm-main",
                Instant.parse("2026-02-01T15:00:10Z"),
                Map.of("visit", Map.of("branchId", "BR-503"))
        ));

        clock.setCurrent(Instant.parse("2026-02-01T15:00:02Z"));
        client.updateBranchState("vm-main", "BR-503",
                new BranchStateUpdateRequest("CLOSED", "09:00-18:00", 0, "vm-old"));
        handler.handle(new IntegrationEvent(
                "evt-visit-503-old",
                "VISIT_CALLED",
                "vm-main",
                Instant.parse("2026-02-01T15:00:05Z"),
                Map.of("visit", Map.of("branchId", "BR-503"))
        ));

        var state = gatewayService.getBranchState("subject", "BR-503", "vm-main");
        Assertions.assertEquals("OPEN", state.status(), "более старое событие не должно перезатирать более новое");
        Assertions.assertEquals("vm-new", state.updatedBy());
    }

    @Test
    void shouldAllowDifferentVisitEventsWithSameOccurredAt() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-505", "vm-main"));
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
        MutableClock clock = new MutableClock(Instant.parse("2026-02-01T15:10:00Z"));
        VisitManagerVisitEventHandler handler = new VisitManagerVisitEventHandler(
                gatewayService,
                new VisitManagerBranchStateEventMapper(),
                cfg,
                clock
        );

        Instant occurredAt = Instant.parse("2026-02-01T15:10:00Z");
        client.updateBranchState("vm-main", "BR-505",
                new BranchStateUpdateRequest("OPEN", "09:00-18:00", 1, "vm-first"));
        handler.handle(new IntegrationEvent(
                "evt-visit-505-a",
                "VISIT_CREATED",
                "vm-main",
                occurredAt,
                Map.of("visit", Map.of("branchId", "BR-505"))
        ));

        clock.setCurrent(Instant.parse("2026-02-01T15:10:02Z"));
        client.updateBranchState("vm-main", "BR-505",
                new BranchStateUpdateRequest("PAUSED", "09:00-18:00", 2, "vm-second"));
        handler.handle(new IntegrationEvent(
                "evt-visit-505-b",
                "VISIT_CREATED",
                "vm-main",
                occurredAt,
                Map.of("visit", Map.of("branchId", "BR-505"))
        ));

        var state = gatewayService.getBranchState("subject", "BR-505", "vm-main");
        Assertions.assertEquals("PAUSED", state.status(),
                "разные события с одинаковым occurredAt не должны теряться");
        Assertions.assertEquals("vm-second", state.updatedBy());
    }

    @Test
    void shouldIgnoreDuplicateVisitEventByEventId() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-506", "vm-main"));
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
        MutableClock clock = new MutableClock(Instant.parse("2026-02-01T15:11:00Z"));
        VisitManagerVisitEventHandler handler = new VisitManagerVisitEventHandler(
                gatewayService,
                new VisitManagerBranchStateEventMapper(),
                cfg,
                clock
        );

        client.updateBranchState("vm-main", "BR-506",
                new BranchStateUpdateRequest("OPEN", "09:00-18:00", 1, "vm-first"));
        handler.handle(new IntegrationEvent(
                "evt-visit-506",
                "VISIT_CREATED",
                "vm-main",
                Instant.parse("2026-02-01T15:11:00Z"),
                Map.of("visit", Map.of("branchId", "BR-506"))
        ));

        clock.setCurrent(Instant.parse("2026-02-01T15:11:02Z"));
        client.updateBranchState("vm-main", "BR-506",
                new BranchStateUpdateRequest("PAUSED", "09:00-18:00", 2, "vm-duplicate"));
        handler.handle(new IntegrationEvent(
                "evt-visit-506",
                "VISIT_CREATED",
                "vm-main",
                Instant.parse("2026-02-01T15:11:01Z"),
                Map.of("visit", Map.of("branchId", "BR-506"))
        ));

        var state = gatewayService.getBranchState("subject", "BR-506", "vm-main");
        Assertions.assertEquals("OPEN", state.status(), "повтор eventId должен игнорироваться");
        Assertions.assertEquals("vm-first", state.updatedBy());
    }

    @Test
    void shouldUsePayloadEventIdForDeduplicationWhenEnvelopeEventIdDiffers() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-507", "vm-main"));
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
        MutableClock clock = new MutableClock(Instant.parse("2026-02-01T15:12:00Z"));
        VisitManagerVisitEventHandler handler = new VisitManagerVisitEventHandler(
                gatewayService,
                new VisitManagerBranchStateEventMapper(),
                cfg,
                clock
        );

        client.updateBranchState("vm-main", "BR-507",
                new BranchStateUpdateRequest("OPEN", "09:00-18:00", 1, "vm-first"));
        handler.handle(new IntegrationEvent(
                "evt-envelope-a",
                "VISIT_CREATED",
                "vm-main",
                null,
                Map.of(
                        "visit", Map.of("branchId", "BR-507", "eventId", "evt-business-507", "occurredAt", "2026-02-01T15:12:00Z"),
                        "meta", Map.of("visitManagerId", "vm-main")
                )
        ));

        clock.setCurrent(Instant.parse("2026-02-01T15:12:03Z"));
        client.updateBranchState("vm-main", "BR-507",
                new BranchStateUpdateRequest("PAUSED", "09:00-18:00", 2, "vm-duplicate"));
        handler.handle(new IntegrationEvent(
                "evt-envelope-b",
                "VISIT_CREATED",
                "vm-main",
                null,
                Map.of(
                        "visit", Map.of("branchId", "BR-507", "eventId", "evt-business-507", "occurredAt", "2026-02-01T15:12:00Z"),
                        "meta", Map.of("visitManagerId", "vm-main")
                )
        ));

        var state = gatewayService.getBranchState("subject", "BR-507", "vm-main");
        Assertions.assertEquals("OPEN", state.status(), "дубликат по payload.eventId должен игнорироваться");
        Assertions.assertEquals("vm-first", state.updatedBy());
    }

    @Test
    void shouldRefreshBranchStateFromNestedDatabusVisitPayload() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-504", "vm-main"));
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
                Clock.fixed(Instant.parse("2026-02-01T16:00:00Z"), ZoneOffset.UTC)
        );

        client.updateBranchState("vm-main", "BR-504",
                new BranchStateUpdateRequest("OPEN", "09:00-18:00", 1, "vm-nested"));
        handler.handle(new IntegrationEvent(
                "evt-visit-504",
                "VISIT_COMPLETED",
                "databus-fallback",
                Instant.parse("2026-02-01T16:00:00Z"),
                Map.of(
                        "data", Map.of(
                                "visit", Map.of("branch", Map.of("id", "BR-504")),
                                "meta", Map.of("visitManagerId", "vm-main")
                        )
                )
        ));

        var state = gatewayService.getBranchState("subject", "BR-504", "vm-main");
        Assertions.assertEquals("OPEN", state.status());
        Assertions.assertEquals("vm-nested", state.updatedBy());
        Assertions.assertEquals("vm-main", state.sourceVisitManagerId());
    }

    @Test
    void shouldCleanupStaleDebounceTrackingEntries() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-601", "vm-main", "BR-602", "vm-main"));
        cfg.setBranchStateEventRefreshDebounce(Duration.ofSeconds(2));

        StubVisitManagerClient client = new StubVisitManagerClient(cfg);
        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                client,
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );
        MutableClock clock = new MutableClock(Instant.parse("2026-02-01T17:00:00Z"));
        VisitManagerVisitEventHandler handler = new VisitManagerVisitEventHandler(
                gatewayService,
                new VisitManagerBranchStateEventMapper(),
                cfg,
                clock
        );

        client.updateBranchState("vm-main", "BR-601",
                new BranchStateUpdateRequest("OPEN", "09:00-18:00", 0, "vm-first"));
        handler.handle(new IntegrationEvent(
                "evt-visit-601",
                "VISIT_CREATED",
                "vm-main",
                Instant.parse("2026-02-01T17:00:00Z"),
                Map.of("visit", Map.of("branchId", "BR-601"))
        ));

        clock.setCurrent(Instant.parse("2026-02-01T17:01:02Z"));
        client.updateBranchState("vm-main", "BR-602",
                new BranchStateUpdateRequest("OPEN", "09:00-18:00", 1, "vm-second"));
        handler.handle(new IntegrationEvent(
                "evt-visit-602",
                "VISIT_CREATED",
                "vm-main",
                Instant.parse("2026-02-01T17:01:02Z"),
                Map.of("visit", Map.of("branchId", "BR-602"))
        ));

        client.updateBranchState("vm-main", "BR-601",
                new BranchStateUpdateRequest("PAUSED", "09:00-18:00", 2, "vm-after-cleanup"));
        handler.handle(new IntegrationEvent(
                "evt-visit-601-old",
                "VISIT_CREATED",
                "vm-main",
                Instant.parse("2026-02-01T16:59:59Z"),
                Map.of("visit", Map.of("branchId", "BR-601"))
        ));

        var state = gatewayService.getBranchState("subject", "BR-601", "vm-main");
        Assertions.assertEquals("PAUSED", state.status(),
                "после cleanup stale-ключ не должен блокировать refresh даже для старого occurredAt");
        Assertions.assertEquals("vm-after-cleanup", state.updatedBy());
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
