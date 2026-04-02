package ru.aritmos.integration.eventing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

class DefaultExternalSystemAudienceResolverTest {

    private final DefaultExternalSystemAudienceResolver resolver = new DefaultExternalSystemAudienceResolver();

    @Test
    void shouldResolveAudienceForVisitAndBranchStateEvents() {
        IntegrationEvent visitEvent = new IntegrationEvent(
                "evt-100",
                "VISIT_CALLED",
                "visit-manager",
                Instant.parse("2026-03-01T10:00:00Z"),
                Map.of()
        );
        IntegrationEvent branchStateEvent = new IntegrationEvent(
                "evt-101",
                "branch-state-updated",
                "visit-manager",
                Instant.parse("2026-03-01T10:00:01Z"),
                Map.of()
        );

        Set<String> visitAudience = resolver.resolve(visitEvent);
        Set<String> branchAudience = resolver.resolve(branchStateEvent);

        Assertions.assertTrue(visitAudience.contains("employee-workplace"));
        Assertions.assertTrue(visitAudience.contains("reception-desk"));
        Assertions.assertEquals(visitAudience, branchAudience);
    }

    @Test
    void shouldMergePayloadTargetSystemsWithDefaultAudience() {
        IntegrationEvent event = new IntegrationEvent(
                "evt-102",
                "VISIT_FINISHED",
                "visit-manager",
                Instant.parse("2026-03-01T10:00:02Z"),
                Map.of("meta", Map.of("targetSystems", new String[]{"employee-analytics", "reception-desk"}))
        );

        Set<String> audience = resolver.resolve(event);

        Assertions.assertTrue(audience.contains("employee-analytics"));
        Assertions.assertTrue(audience.contains("employee-workplace"));
        Assertions.assertTrue(audience.contains("reception-desk"));
    }
}
