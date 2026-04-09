package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.nio.file.Path;

/**
 * Фабрика выбора хранилища скриптов (Redis, file-backed или fallback in-memory).
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
        if (configuration.getProgrammableApi().getScriptStorage().getFile().isEnabled()) {
            return new FileGroovyScriptStorage(
                    Path.of(configuration.getProgrammableApi().getScriptStorage().getFile().getPath()),
                    objectMapper
            );
        }
        return inMemoryStorage;
    }
}
