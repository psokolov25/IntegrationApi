package ru.suo.integration.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.suo.integration.config.IntegrationGatewayConfiguration;
import ru.suo.integration.eventing.DefaultVisitCreatedEventHandler;
import ru.suo.integration.eventing.EventDispatcherService;
import ru.suo.integration.eventing.EventInboxService;
import ru.suo.integration.eventing.EventRetryService;
import ru.suo.integration.eventing.EventStoreService;

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
