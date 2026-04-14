package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Фабрика выбора storage-режима inbox/outbox: MEMORY, FILE, REDIS.
 */
@Factory
public class EventingInboxOutboxStorageFactory {

    @Singleton
    public EventingInboxOutboxStorage eventingInboxOutboxStorage(IntegrationGatewayConfiguration configuration,
                                                                 ObjectMapper objectMapper,
                                                                 InMemoryEventingInboxOutboxStorage inMemoryStorage) {
        IntegrationGatewayConfiguration.EventingStorageSettings storage = configuration.getEventing().getStorage();
        String mode = storage.getMode() == null ? "MEMORY" : storage.getMode().trim().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "FILE" -> new FileEventingInboxOutboxStorage(Path.of(storage.getFile().getPath()), objectMapper);
            case "REDIS" -> new RedisEventingInboxOutboxStorage(storage.getRedis(), objectMapper);
            case "MEMORY" -> inMemoryStorage;
            default -> throw new IllegalArgumentException("Неподдерживаемый режим storage inbox/outbox: " + mode);
        };
    }
}

