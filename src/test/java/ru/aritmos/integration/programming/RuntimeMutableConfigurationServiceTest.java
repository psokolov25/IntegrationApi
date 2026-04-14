package ru.aritmos.integration.programming;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.InMemoryEventingInboxOutboxStorage;

import java.util.Map;

class RuntimeMutableConfigurationServiceTest {

    @Test
    void shouldApplyAndPersistRuntimeSettingsToStorage() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        InMemoryEventingInboxOutboxStorage storage = new InMemoryEventingInboxOutboxStorage();
        RuntimeMutableConfigurationService service = new RuntimeMutableConfigurationService(cfg, storage);

        Map<String, Object> applied = service.apply(Map.of(
                "aggregateMaxBranches", 50,
                "aggregateRequestTimeoutMillis", 1500,
                "outboxBackoffSeconds", 3,
                "outboxMaxAttempts", 10,
                "inboxProcessingTimeoutSeconds", 60,
                "outboxAutoFlushBatchSize", 25,
                "maxPayloadFields", 80,
                "httpProcessing", Map.of("responseBodyMaxChars", 5000)
        ));

        Assertions.assertEquals(50, cfg.getAggregateMaxBranches());
        Assertions.assertEquals(1500, cfg.getAggregateRequestTimeoutMillis());
        Assertions.assertEquals(25, cfg.getEventing().getOutboxAutoFlushBatchSize());
        Assertions.assertEquals(5000, cfg.getProgrammableApi().getHttpProcessing().getResponseBodyMaxChars());
        Assertions.assertEquals(50, applied.get("aggregateMaxBranches"));
        Assertions.assertFalse(storage.loadRuntimeSettings().isEmpty());
    }

    @Test
    void shouldRestoreSavedSettingsOnInit() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        InMemoryEventingInboxOutboxStorage storage = new InMemoryEventingInboxOutboxStorage();
        storage.saveRuntimeSettings(Map.of(
                "aggregateMaxBranches", 77,
                "aggregateRequestTimeoutMillis", 2200,
                "outboxBackoffSeconds", 4,
                "outboxMaxAttempts", 11,
                "inboxProcessingTimeoutSeconds", 88,
                "outboxAutoFlushBatchSize", 33,
                "maxPayloadFields", 144,
                "httpProcessing", Map.of("responseBodyMaxChars", 3500)
        ));

        RuntimeMutableConfigurationService service = new RuntimeMutableConfigurationService(cfg, storage);
        service.init();

        Assertions.assertEquals(77, cfg.getAggregateMaxBranches());
        Assertions.assertEquals(2200, cfg.getAggregateRequestTimeoutMillis());
        Assertions.assertEquals(33, cfg.getEventing().getOutboxAutoFlushBatchSize());
        Assertions.assertEquals(3500, cfg.getProgrammableApi().getHttpProcessing().getResponseBodyMaxChars());
    }
}

