package ru.aritmos.integration.eventing.visitmanager;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.IntegrationEvent;

import java.time.Instant;
import java.util.Map;

class VisitManagerBranchStateEventMapperTest {

    private final VisitManagerBranchStateEventMapper mapper = new VisitManagerBranchStateEventMapper();

    @Test
    void shouldMapVisitManagerDatabusV1Payload() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-1",
                "branch-state-updated",
                "databus-visitmanager",
                Instant.parse("2026-02-01T11:00:00Z"),
                Map.of(
                        "meta", Map.of("visitManagerId", "vm-main"),
                        "data", Map.of(
                                "branch", Map.of("id", "BR-100"),
                                "state", Map.of(
                                        "code", "PAUSED",
                                        "activeWindow", "10:00-19:00",
                                        "queueSize", 4,
                                        "updatedAt", "2026-02-01T10:59:59Z",
                                        "updatedBy", "vm-operator"
                                )
                        )
                )
        );

        VisitManagerBranchStateEventPayload payload = mapper.map(event);
        Assertions.assertEquals("vm-main", payload.sourceVisitManagerId());
        Assertions.assertEquals("BR-100", payload.branchId());
        Assertions.assertEquals("PAUSED", payload.status());
        Assertions.assertEquals(4, payload.queueSize());
    }

    @Test
    void shouldUseEnvelopeSourceWhenVisitManagerIdMissing() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-2",
                "branch-state-updated",
                "vm-fallback",
                Instant.parse("2026-02-01T11:05:00Z"),
                Map.of(
                        "data", Map.of(
                                "branch", Map.of("id", "BR-101"),
                                "state", Map.of(
                                        "status", "OPEN",
                                        "activeWindow", "09:00-18:00"
                                )
                        )
                )
        );

        VisitManagerBranchStateEventPayload payload = mapper.map(event);
        Assertions.assertEquals("vm-fallback", payload.sourceVisitManagerId());
        Assertions.assertEquals("OPEN", payload.status());
    }

    @Test
    void shouldMapVisitLifecycleEventPayload() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-3",
                "VISIT_CALLED",
                "vm-1",
                Instant.parse("2026-02-01T11:06:00Z"),
                Map.of(
                        "visit", Map.of("branchId", "BR-201"),
                        "meta", Map.of("visitManagerId", "vm-main")
                )
        );

        VisitManagerVisitEventPayload payload = mapper.mapVisitEvent(event);
        Assertions.assertEquals("vm-main", payload.sourceVisitManagerId());
        Assertions.assertEquals("BR-201", payload.branchId());
        Assertions.assertEquals("VISIT_CALLED", payload.visitEventType());
    }

    @Test
    void shouldRecognizeAndMapEntityChangedBranchPayload() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-4",
                "ENTITY_CHANGED",
                "vm-main",
                Instant.parse("2026-02-01T11:08:00Z"),
                Map.of(
                        "meta", Map.of("visitManagerId", "vm-main"),
                        "data", Map.of(
                                "class", "ru.psokolov.visitmanager.Branch",
                                "entity", Map.of(
                                        "id", "BR-301",
                                        "status", "OPEN",
                                        "activeWindow", "09:00-20:00",
                                        "queueSize", 7,
                                        "updatedAt", "2026-02-01T11:07:55Z",
                                        "updatedBy", "entity-updater"
                                )
                        )
                )
        );

        Assertions.assertTrue(mapper.isBranchEntityChanged(event));
        VisitManagerBranchStateEventPayload payload = mapper.mapEntityChangedBranch(event);
        Assertions.assertEquals("vm-main", payload.sourceVisitManagerId());
        Assertions.assertEquals("BR-301", payload.branchId());
        Assertions.assertEquals("OPEN", payload.status());
        Assertions.assertEquals(7, payload.queueSize());
    }

    @Test
    void shouldUseConfiguredPathsForEntityChangedBranchPayload() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().getEntityChangedBranchMapping().setClassNamePaths(java.util.List.of("entity.type"));
        cfg.getEventing().getEntityChangedBranchMapping().setAcceptedClassNames(java.util.List.of("BranchPayload"));
        cfg.getEventing().getEntityChangedBranchMapping().setBranchIdPaths(java.util.List.of("entity.branch.key"));
        cfg.getEventing().getEntityChangedBranchMapping().setStatusPaths(java.util.List.of("entity.branch.lifecycle.state"));
        cfg.getEventing().getEntityChangedBranchMapping().setActiveWindowPaths(java.util.List.of("entity.branch.window"));
        cfg.getEventing().getEntityChangedBranchMapping().setQueueSizePaths(java.util.List.of("entity.branch.queue.current"));
        cfg.getEventing().getEntityChangedBranchMapping().setUpdatedAtPaths(java.util.List.of("entity.branch.timeline.updatedAt"));
        cfg.getEventing().getEntityChangedBranchMapping().setUpdatedByPaths(java.util.List.of("entity.branch.timeline.updatedBy"));
        cfg.getEventing().getEntityChangedBranchMapping().setVisitManagerIdPaths(java.util.List.of("entity.meta.vm"));
        VisitManagerBranchStateEventMapper configurableMapper = new VisitManagerBranchStateEventMapper(cfg);

        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-5",
                "ENTITY_CHANGED",
                "vm-ignored",
                Instant.parse("2026-02-01T11:09:00Z"),
                Map.of(
                        "entity", Map.of(
                                "type", "BranchPayload",
                                "meta", Map.of("vm", "vm-alt"),
                                "branch", Map.of(
                                        "key", "BR-777",
                                        "window", "10:00-21:00",
                                        "lifecycle", Map.of("state", "PAUSED"),
                                        "queue", Map.of("current", 3),
                                        "timeline", Map.of(
                                                "updatedAt", "2026-02-01T11:08:59Z",
                                                "updatedBy", "groovy-rule"
                                        )
                                )
                        )
                )
        );

        Assertions.assertTrue(configurableMapper.isBranchEntityChanged(event));
        VisitManagerBranchStateEventPayload payload = configurableMapper.mapEntityChangedBranch(event);
        Assertions.assertEquals("vm-alt", payload.sourceVisitManagerId());
        Assertions.assertEquals("BR-777", payload.branchId());
        Assertions.assertEquals("PAUSED", payload.status());
        Assertions.assertEquals("groovy-rule", payload.updatedBy());
    }
}
