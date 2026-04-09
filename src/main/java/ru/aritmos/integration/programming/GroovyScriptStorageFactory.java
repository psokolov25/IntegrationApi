package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

/**
 * Фабрика выбора хранилища скриптов (Redis или fallback in-memory).
 */
@Factory
public class GroovyScriptStorageFactory {

    @Singleton
    public GroovyScriptStorage groovyScriptStorage(IntegrationGatewayConfiguration configuration,
                                                   ObjectMapper objectMapper,
                                                   InMemoryGroovyScriptStorage inMemoryStorage) {
        if (configuration.getProgrammableApi().getScriptStorage().getRedis().isEnabled()) {
            return new RedisGroovyScriptStorage(configuration, objectMapper);
        }
        return inMemoryStorage;
    }
}
