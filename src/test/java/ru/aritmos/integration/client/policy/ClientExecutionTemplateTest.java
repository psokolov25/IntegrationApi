package ru.aritmos.integration.client.policy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.util.concurrent.atomic.AtomicInteger;

class ClientExecutionTemplateTest {

    @Test
    void shouldRetryAndEventuallySucceed() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getClientPolicy().setRetryAttempts(2);
        cfg.getClientPolicy().setTimeoutMillis(1000);

        ClientExecutionTemplate template = new ClientExecutionTemplate(cfg);
        AtomicInteger counter = new AtomicInteger();

        Integer result = template.execute("client-a", () -> {
            int attempt = counter.incrementAndGet();
            if (attempt < 3) {
                throw new IllegalStateException("fail");
            }
            return 42;
        });

        Assertions.assertEquals(42, result);
        Assertions.assertEquals(3, counter.get());
    }

    @Test
    void shouldOpenCircuitAfterThreshold() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getClientPolicy().setRetryAttempts(0);
        cfg.getClientPolicy().setCircuitFailureThreshold(1);
        cfg.getClientPolicy().setCircuitOpenSeconds(30);

        ClientExecutionTemplate template = new ClientExecutionTemplate(cfg);

        Assertions.assertThrows(RuntimeException.class, () -> template.execute("client-a", () -> {
            throw new IllegalStateException("boom");
        }));

        Assertions.assertThrows(IllegalStateException.class, () -> template.execute("client-a", () -> 1));
    }
}
