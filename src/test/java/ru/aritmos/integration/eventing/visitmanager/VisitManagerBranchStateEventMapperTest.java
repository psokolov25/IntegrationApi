package ru.aritmos.integration.eventing.visitmanager;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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
}
