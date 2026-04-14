package ru.aritmos.integration.eventing.visitmanager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.IntegrationEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class VisitManagerBranchStateEventMapperTest {

    private final VisitManagerBranchStateEventMapper mapper = new VisitManagerBranchStateEventMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        Assertions.assertEquals("evt-vm-1", payload.canonicalEventId());
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
    void shouldMapBranchStateUpdatedWithNestedTargetVisitManagerId() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-2b",
                "branch-state-updated",
                "vm-fallback",
                Instant.parse("2026-02-01T11:05:30Z"),
                Map.of(
                        "meta", Map.of("targetVisitManagerId", "vm-nested-main"),
                        "data", Map.of(
                                "branch", Map.of("id", "BR-101-B"),
                                "state", Map.of(
                                        "status", "OPEN",
                                        "activeWindow", "09:00-18:00"
                                )
                        )
                )
        );

        VisitManagerBranchStateEventPayload payload = mapper.map(event);
        Assertions.assertEquals("vm-nested-main", payload.sourceVisitManagerId());
        Assertions.assertEquals("BR-101-B", payload.branchId());
    }

    @Test
    void shouldMapBranchStateUpdatedWithSnakeCaseTargetVisitManagerId() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-2c",
                "branch-state-updated",
                "vm-fallback",
                Instant.parse("2026-02-01T11:05:45Z"),
                Map.of(
                        "data", Map.of(
                                "meta", Map.of("target_visit_manager_id", "vm-snake-main"),
                                "branch", Map.of("id", "BR-101-C"),
                                "state", Map.of(
                                        "status", "OPEN",
                                        "activeWindow", "09:00-18:00"
                                )
                        )
                )
        );

        VisitManagerBranchStateEventPayload payload = mapper.map(event);
        Assertions.assertEquals("vm-snake-main", payload.sourceVisitManagerId());
        Assertions.assertEquals("BR-101-C", payload.branchId());
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
        Assertions.assertEquals(Instant.parse("2026-02-01T11:06:00Z"), payload.occurredAt());
        Assertions.assertEquals("evt-vm-3", payload.canonicalEventId());
    }

    @Test
    void shouldMapVisitLifecycleEventPayloadWithConfiguredCanonicalPaths() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-3c",
                "VISIT_COMPLETED",
                "vm-source-fallback",
                Instant.parse("2026-02-01T11:07:00Z"),
                Map.of(
                        "data", Map.of(
                                "visit", Map.of("branch", Map.of("id", "BR-202")),
                                "meta", Map.of("visitManagerId", "vm-metadata-main")
                        )
                )
        );

        VisitManagerVisitEventPayload payload = mapper.mapVisitEvent(event);
        Assertions.assertEquals("vm-metadata-main", payload.sourceVisitManagerId());
        Assertions.assertEquals("BR-202", payload.branchId());
        Assertions.assertEquals("VISIT_COMPLETED", payload.visitEventType());
        Assertions.assertEquals("evt-vm-3c", payload.canonicalEventId());
    }

    @Test
    void shouldMapVisitLifecycleEventFromNestedEntitiesWithBranchObject() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().getVisitEventMapping().setBranchIdPaths(List.of("data.entities.*.visit.branch.id"));
        cfg.getEventing().getVisitEventMapping().setVisitManagerIdPaths(List.of("data.entities.*.meta.target_visit_manager_id"));
        VisitManagerBranchStateEventMapper configurableMapper = new VisitManagerBranchStateEventMapper(cfg);

        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-3b",
                "VISIT_REDIRECTED",
                "vm-fallback-source",
                Instant.parse("2026-04-10T09:10:00Z"),
                Map.of(
                        "data", Map.of(
                                "entities", List.of(
                                        Map.of(
                                                "meta", Map.of("target_visit_manager_id", "vm-nested"),
                                                "visit", Map.of(
                                                        "branch", Map.of("id", "BR-ENTITY-901")
                                                )
                                        )
                                )
                        )
                )
        );

        VisitManagerVisitEventPayload payload = configurableMapper.mapVisitEvent(event);
        Assertions.assertEquals("vm-nested", payload.sourceVisitManagerId());
        Assertions.assertEquals("BR-ENTITY-901", payload.branchId());
        Assertions.assertEquals("VISIT_REDIRECTED", payload.visitEventType());
    }

    @Test
    void shouldExtractVisitEventIdentityAndOccurredAtFromPayloadFallback() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-envelope-ignored",
                "VISIT_CREATED",
                "vm-source-fallback",
                null,
                Map.of(
                        "data", Map.of(
                                "visit", Map.of(
                                        "branch", Map.of("id", "BR-ENTITY-902"),
                                        "eventId", "evt-canonical-902",
                                        "occurredAt", "2026-04-10T10:15:30Z"
                                ),
                                "meta", Map.of("visitManagerId", "vm-main")
                        )
                )
        );

        VisitManagerVisitEventPayload payload = mapper.mapVisitEvent(event);
        Assertions.assertEquals("vm-main", payload.sourceVisitManagerId());
        Assertions.assertEquals("BR-ENTITY-902", payload.branchId());
        Assertions.assertEquals("evt-canonical-902", payload.canonicalEventId());
        Assertions.assertEquals(Instant.parse("2026-04-10T10:15:30Z"), payload.occurredAt());
    }

    @Test
    void shouldMapVisitEventFromDatabusEnvelopeWithDefaultPaths() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-envelope-903",
                "VISIT_SET_PARAMETER_MAP",
                "vm-source-fallback",
                Instant.parse("2026-04-10T11:15:30Z"),
                Map.of(
                        "event_id", "evt-canonical-903",
                        "timestamp", "2026-04-10T11:15:00Z",
                        "meta", Map.of("targetVisitManagerId", "vm-main-903"),
                        "data", Map.of(
                                "entities", List.of(
                                        Map.of(
                                                "visit", Map.of(
                                                        "branch", Map.of("id", "BR-ENTITY-903")
                                                )
                                        )
                                )
                        )
                )
        );

        VisitManagerVisitEventPayload payload = mapper.mapVisitEvent(event);
        Assertions.assertEquals("vm-main-903", payload.sourceVisitManagerId());
        Assertions.assertEquals("BR-ENTITY-903", payload.branchId());
        Assertions.assertEquals("evt-canonical-903", payload.canonicalEventId());
        Assertions.assertEquals(Instant.parse("2026-04-10T11:15:00Z"), payload.occurredAt());
        Assertions.assertEquals("VISIT_SET_PARAMETER_MAP", payload.visitEventType());
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
        Assertions.assertEquals("evt-vm-4", payload.canonicalEventId());
    }

    @Test
    void shouldExtractBranchStateCanonicalEventIdFromPayloadWhenEnvelopeChanges() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-envelope-branch",
                "branch-state-updated",
                "vm-main",
                Instant.parse("2026-04-13T11:00:00Z"),
                Map.of(
                        "eventId", "evt-canonical-branch-1",
                        "branchId", "BR-100",
                        "targetVisitManagerId", "vm-main",
                        "status", "OPEN",
                        "activeWindow", "09:00-18:00",
                        "queueSize", 1
                )
        );

        VisitManagerBranchStateEventPayload payload = mapper.map(event);
        Assertions.assertEquals("evt-canonical-branch-1", payload.canonicalEventId());
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

    @Test
    void shouldMapEntityChangedBranchFromExamplesSnapshotPayload() throws IOException {
        Path examplesPayloadPath = Path.of("examples", "message-body-18613411-29e1-4dc9-afe4-ba37ab7e5e04.json");
        Map<String, Object> payload = objectMapper.readValue(
                Files.readString(examplesPayloadPath),
                new TypeReference<>() {
                }
        );
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-6",
                "ENTITY_CHANGED",
                "vm-examples",
                Instant.parse("2026-04-08T13:30:00Z"),
                payload
        );

        Assertions.assertTrue(mapper.isBranchEntityChanged(event));
        VisitManagerBranchStateEventPayload mapped = mapper.mapEntityChangedBranch(event);
        Assertions.assertEquals("vm-examples", mapped.sourceVisitManagerId());
        Assertions.assertEquals("cd842979-3dc1-4505-a1ae-9a92f0622da2", mapped.branchId());
        Assertions.assertEquals("UNKNOWN", mapped.status());
        Assertions.assertEquals("11:24:00", mapped.activeWindow());
        Assertions.assertEquals(1, mapped.queueSize());
    }

    @Test
    void shouldInferQueueSizeWhenServicePointsComeAsArray() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-7",
                "ENTITY_CHANGED",
                "vm-main",
                Instant.parse("2026-04-09T10:15:00Z"),
                Map.of(
                        "className", "ru.psokolov.visitmanager.Branch",
                        "entityId", "BR-ARRAY-1",
                        "newValue", Map.of(
                                "id", "BR-ARRAY-1",
                                "servicePoints", java.util.List.of(
                                        Map.of("id", "sp-1", "visits", java.util.List.of(Map.of("id", "v-1"))),
                                        Map.of("id", "sp-2", "visits", java.util.List.of(Map.of("id", "v-2"), Map.of("id", "v-3"))),
                                        Map.of("id", "sp-3", "visits", Map.of("v-4", Map.of("id", "v-4")))
                                )
                        )
                )
        );

        Assertions.assertTrue(mapper.isBranchEntityChanged(event));
        VisitManagerBranchStateEventPayload mapped = mapper.mapEntityChangedBranch(event);
        Assertions.assertEquals(4, mapped.queueSize());
    }

    @Test
    void shouldResolveEntityChangedFieldsInsideCollectionsAndSnakeCaseKeys() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-8",
                "ENTITY_CHANGED",
                "vm-main",
                Instant.parse("2026-04-09T10:25:00Z"),
                Map.of(
                        "data", Map.of(
                                "entities", List.of(
                                        Map.of(
                                                "class_name", "ru.psokolov.visitmanager.Branch",
                                                "new_value", Map.of(
                                                        "branch_id", "BR-COLLECTION-1",
                                                        "active_window", "08:00-20:00",
                                                        "service_points", List.of(
                                                                Map.of("visits", List.of(Map.of("id", "v-1"), Map.of("id", "v-2")))
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        Assertions.assertTrue(mapper.isBranchEntityChanged(event));
        VisitManagerBranchStateEventPayload mapped = mapper.mapEntityChangedBranch(event);
        Assertions.assertEquals("BR-COLLECTION-1", mapped.branchId());
        Assertions.assertEquals("08:00-20:00", mapped.activeWindow());
        Assertions.assertEquals(2, mapped.queueSize());
    }

    @Test
    void shouldInferQueueSizeFromQueuesWhenServicePointsMissing() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-9",
                "ENTITY_CHANGED",
                "vm-main",
                Instant.parse("2026-04-09T10:30:00Z"),
                Map.of(
                        "className", "Branch",
                        "newValue", Map.of(
                                "id", "BR-QUEUE-1",
                                "queues", Map.of(
                                        "q-1", Map.of("visits", List.of(Map.of("id", "v-1"))),
                                        "q-2", Map.of("visits", List.of(Map.of("id", "v-2"), Map.of("id", "v-3")))
                                )
                        )
                )
        );

        VisitManagerBranchStateEventPayload mapped = mapper.mapEntityChangedBranch(event);
        Assertions.assertEquals(3, mapped.queueSize());
    }

    @Test
    void shouldUseConfigurableWrapperAndQueueSnapshotKeys() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().getEntityChangedBranchMapping().setWrapperKeys(List.of("records", "payload"));
        cfg.getEventing().getEntityChangedBranchMapping().setBranchIdPaths(List.of("payload.records.0.after_state.id"));
        cfg.getEventing().getEntityChangedBranchMapping().setQueueSnapshotRoots(List.of("payload.records.0.after_state"));
        cfg.getEventing().getEntityChangedBranchMapping().setServicePointsKeys(List.of("desks"));
        cfg.getEventing().getEntityChangedBranchMapping().setVisitsKeys(List.of("tickets"));
        VisitManagerBranchStateEventMapper configurableMapper = new VisitManagerBranchStateEventMapper(cfg);

        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-10",
                "ENTITY_CHANGED",
                "vm-main",
                Instant.parse("2026-04-09T11:00:00Z"),
                Map.of(
                        "className", "Branch",
                        "payload", Map.of(
                                "records", List.of(
                                        Map.of(
                                                "after_state", Map.of(
                                                        "id", "BR-CFG-1",
                                                        "desks", Map.of(
                                                                "d-1", Map.of("tickets", List.of(Map.of("id", "t1"), Map.of("id", "t2")))
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        VisitManagerBranchStateEventPayload mapped = configurableMapper.mapEntityChangedBranch(event);
        Assertions.assertEquals("BR-CFG-1", mapped.branchId());
        Assertions.assertEquals(2, mapped.queueSize());
    }


    @Test
    void shouldParseUpdatedAtFromEpochSecondsAndMillis() {
        IntegrationEvent secondsEvent = new IntegrationEvent(
                "evt-vm-12",
                "branch-state-updated",
                "vm-main",
                Instant.parse("2026-04-09T11:30:00Z"),
                Map.of(
                        "branchId", "BR-EPOCH-SECONDS",
                        "targetVisitManagerId", "vm-main",
                        "status", "OPEN",
                        "activeWindow", "09:00-18:00",
                        "updatedAt", 1775734200
                )
        );

        IntegrationEvent millisEvent = new IntegrationEvent(
                "evt-vm-13",
                "branch-state-updated",
                "vm-main",
                Instant.parse("2026-04-09T11:30:00Z"),
                Map.of(
                        "branchId", "BR-EPOCH-MILLIS",
                        "targetVisitManagerId", "vm-main",
                        "status", "OPEN",
                        "activeWindow", "09:00-18:00",
                        "updatedAt", "1775734200000"
                )
        );

        VisitManagerBranchStateEventPayload secondsPayload = mapper.map(secondsEvent);
        VisitManagerBranchStateEventPayload millisPayload = mapper.map(millisEvent);

        Assertions.assertEquals(Instant.ofEpochSecond(1775734200L), secondsPayload.updatedAt());
        Assertions.assertEquals(Instant.ofEpochMilli(1775734200000L), millisPayload.updatedAt());
    }

    @Test
    void shouldSupportWildcardSegmentsInConfiguredPaths() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().getEntityChangedBranchMapping().setBranchIdPaths(List.of("payload.records.*.after_state.id"));
        cfg.getEventing().getEntityChangedBranchMapping().setQueueSnapshotRoots(List.of("payload.records.*.after_state"));
        cfg.getEventing().getEntityChangedBranchMapping().setServicePointsKeys(List.of("desks"));
        cfg.getEventing().getEntityChangedBranchMapping().setVisitsKeys(List.of("tickets"));
        VisitManagerBranchStateEventMapper configurableMapper = new VisitManagerBranchStateEventMapper(cfg);

        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-11",
                "ENTITY_CHANGED",
                "vm-main",
                Instant.parse("2026-04-09T11:20:00Z"),
                Map.of(
                        "className", "Branch",
                        "payload", Map.of(
                                "records", List.of(
                                        Map.of("ignored", true),
                                        Map.of(
                                                "after_state", Map.of(
                                                        "id", "BR-WILDCARD-1",
                                                        "desks", List.of(
                                                                Map.of("tickets", List.of(Map.of("id", "t1")))
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        VisitManagerBranchStateEventPayload mapped = configurableMapper.mapEntityChangedBranch(event);
        Assertions.assertEquals("BR-WILDCARD-1", mapped.branchId());
        Assertions.assertEquals(1, mapped.queueSize());
    }

    @Test
    void shouldUseEntityChangedDefaultsForNestedMetaVisitManagerAndNewValueUpdatedAt() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-vm-14",
                "ENTITY_CHANGED",
                "vm-fallback",
                Instant.parse("2026-04-09T11:45:00Z"),
                Map.of(
                        "className", "Branch",
                        "data", Map.of(
                                "meta", Map.of("targetVisitManagerId", "vm-nested-meta"),
                                "entity", Map.of("id", "BR-NESTED-META-1")
                        ),
                        "newValue", Map.of(
                                "id", "BR-NESTED-META-1",
                                "status", "OPEN",
                                "activeWindow", "08:00-19:00",
                                "queueSize", 6,
                                "updatedAt", "2026-04-09T11:44:55Z",
                                "updatedBy", "entity-changed-sync"
                        )
                )
        );

        VisitManagerBranchStateEventPayload mapped = mapper.mapEntityChangedBranch(event);
        Assertions.assertEquals("vm-nested-meta", mapped.sourceVisitManagerId());
        Assertions.assertEquals(Instant.parse("2026-04-09T11:44:55Z"), mapped.updatedAt());
        Assertions.assertEquals("entity-changed-sync", mapped.updatedBy());
        Assertions.assertEquals("BR-NESTED-META-1", mapped.branchId());
    }

    @Test
    void shouldUseConfiguredPathsForVisitEventPayload() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().getVisitEventMapping().setBranchIdPaths(List.of("payload.branch.code"));
        cfg.getEventing().getVisitEventMapping().setVisitManagerIdPaths(List.of("payload.targetVm"));
        cfg.getEventing().getVisitEventMapping().setOccurredAtPaths(List.of("payload.meta.time"));
        cfg.getEventing().getVisitEventMapping().setEventIdPaths(List.of("payload.meta.id"));

        VisitManagerBranchStateEventMapper configuredMapper = new VisitManagerBranchStateEventMapper(cfg);
        IntegrationEvent event = new IntegrationEvent(
                "evt-envelope-fallback",
                "VISIT_CREATED",
                "vm-envelope",
                Instant.parse("2026-04-13T10:00:00Z"),
                Map.of(
                        "payload", Map.of(
                                "branch", Map.of("code", "BR-910"),
                                "targetVm", "vm-config",
                                "meta", Map.of(
                                        "time", "2026-04-13T09:59:30Z",
                                        "id", "evt-config-910"
                                )
                        )
                )
        );

        VisitManagerVisitEventPayload payload = configuredMapper.mapVisitEvent(event);
        Assertions.assertEquals("BR-910", payload.branchId());
        Assertions.assertEquals("vm-config", payload.sourceVisitManagerId());
        Assertions.assertEquals(Instant.parse("2026-04-13T09:59:30Z"), payload.occurredAt());
        Assertions.assertEquals("evt-config-910", payload.canonicalEventId());
    }
}
