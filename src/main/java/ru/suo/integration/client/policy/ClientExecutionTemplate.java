package ru.suo.integration.client.policy;

import jakarta.inject.Singleton;
import ru.suo.integration.config.IntegrationGatewayConfiguration;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Простейший retry/timeout/circuit-breaker шаблон для integration clients.
 */
@Singleton
public class ClientExecutionTemplate {

    private final IntegrationGatewayConfiguration configuration;
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final Map<String, Instant> openedCircuits = new ConcurrentHashMap<>();

    public ClientExecutionTemplate(IntegrationGatewayConfiguration configuration) {
        this.configuration = configuration;
    }

    public <T> T execute(String clientId, Supplier<T> action) {
        ensureCircuitClosed(clientId);

        int attempts = Math.max(1, configuration.getClientPolicy().getRetryAttempts() + 1);
        RuntimeException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                T value = CompletableFuture.supplyAsync(action)
                        .orTimeout(configuration.getClientPolicy().getTimeoutMillis(), TimeUnit.MILLISECONDS)
                        .join();
                failures.put(clientId, 0);
                return value;
            } catch (Exception ex) {
                last = new RuntimeException(ex);
            }
        }

        int total = failures.compute(clientId, (k, v) -> v == null ? 1 : v + 1);
        if (total >= configuration.getClientPolicy().getCircuitFailureThreshold()) {
            openedCircuits.put(clientId, Instant.now().plusSeconds(configuration.getClientPolicy().getCircuitOpenSeconds()));
        }
        throw last == null ? new IllegalStateException("Ошибка исполнения клиента") : last;
    }

    private void ensureCircuitClosed(String clientId) {
        Instant openUntil = openedCircuits.get(clientId);
        if (openUntil == null) {
            return;
        }
        if (Instant.now().isAfter(openUntil)) {
            openedCircuits.remove(clientId);
            failures.put(clientId, 0);
            return;
        }
        throw new IllegalStateException("Circuit open for client: " + clientId);
    }
}
