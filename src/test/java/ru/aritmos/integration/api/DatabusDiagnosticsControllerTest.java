package ru.aritmos.integration.api;

import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.DefaultVisitCreatedEventHandler;
import ru.aritmos.integration.eventing.EventDispatcherService;
import ru.aritmos.integration.eventing.EventInboxService;
import ru.aritmos.integration.eventing.EventOutboxService;
import ru.aritmos.integration.eventing.EventRetryService;
import ru.aritmos.integration.eventing.EventStoreService;
import ru.aritmos.integration.eventing.IntegrationEvent;
import ru.aritmos.integration.security.RequestSecurityContext;
import ru.aritmos.integration.security.core.AuthorizationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

class DatabusDiagnosticsControllerTest {

    @Test
    void shouldReturnTopTargetsForLegacyDashboard() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().setEnabled(true);
        EventRetryService retryService = new EventRetryService();
        EventDispatcherService dispatcherService = new EventDispatcherService(
                configuration,
                new EventInboxService(),
                retryService,
                new EventStoreService(),
                new EventOutboxService(),
                event -> {
                },
                List.of(new DefaultVisitCreatedEventHandler())
        );
        DatabusDiagnosticsController controller = new DatabusDiagnosticsController(
                dispatcherService,
                retryService,
                new AuthorizationService()
        );

        dispatcherService.process(new IntegrationEvent(
                "evt-1",
                "visit-created",
                "agent-east",
                Instant.parse("2026-04-14T05:00:00Z"),
                Map.of("branchId", "BR-1")
        ));
        dispatcherService.process(new IntegrationEvent(
                "evt-2",
                "visit-created",
                "agent-east",
                Instant.parse("2026-04-14T05:01:00Z"),
                Map.of("branchId", "BR-2")
        ));
        dispatcherService.process(new IntegrationEvent(
                "evt-3",
                "visit-created",
                "agent-west",
                Instant.parse("2026-04-14T05:02:00Z"),
                Map.of("branchId", "BR-3")
        ));

        HttpRequest<?> request = HttpRequest.GET("/databus/diagnostics/dashboard");
        RequestSecurityContext.attach(request, new SubjectPrincipal("ops", Set.of("event-process")));

        Map<String, Object> payload = controller.dashboard(request, 1, true);
        Assertions.assertEquals(3L, payload.get("processedCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topTargets = (List<Map<String, Object>>) payload.get("topTargets");
        Assertions.assertEquals(1, topTargets.size());
        Assertions.assertEquals("agent-east", topTargets.get(0).get("target"));
        Assertions.assertEquals(2L, topTargets.get(0).get("count"));
    }
}
