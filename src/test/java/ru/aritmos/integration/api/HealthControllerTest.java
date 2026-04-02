package ru.aritmos.integration.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.DefaultVisitCreatedEventHandler;
import ru.aritmos.integration.eventing.EventDispatcherService;
import ru.aritmos.integration.eventing.EventInboxService;
import ru.aritmos.integration.eventing.EventRetryService;
import ru.aritmos.integration.eventing.EventStoreService;

import java.util.List;

class HealthControllerTest {

    @Test
    void shouldReturnUpWhenEventingDisabled() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().setEnabled(false);
        EventDispatcherService dispatcher = new EventDispatcherService(
                cfg,
                new EventInboxService(),
                new EventRetryService(),
                new EventStoreService(),
                event -> {},
                List.of(new DefaultVisitCreatedEventHandler())
        );
        HealthController controller = new HealthController(cfg, dispatcher);

        var readiness = controller.readiness();
        Assertions.assertEquals("UP", readiness.status());
        Assertions.assertEquals("DISABLED", readiness.components().get("eventing"));
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
        HealthController controller = new HealthController(cfg, dispatcher);

        var liveness = controller.liveness();
        Assertions.assertEquals("UP", liveness.status());
    }
}
