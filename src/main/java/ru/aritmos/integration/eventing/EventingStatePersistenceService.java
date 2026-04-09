package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Файловая персистентность snapshot состояния eventing для восстановления после перезапуска.
 */
@Singleton
public class EventingStatePersistenceService {

    private final IntegrationGatewayConfiguration configuration;
    private final ObjectMapper objectMapper;

    public EventingStatePersistenceService(IntegrationGatewayConfiguration configuration,
                                           ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
    }

    public boolean enabled() {
        return configuration.getEventing().isStatePersistenceEnabled();
    }

    public synchronized void save(EventingSnapshot snapshot) {
        if (!enabled() || snapshot == null) {
            return;
        }
        Path path = statePath();
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), snapshot);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось сохранить eventing snapshot", ex);
        }
    }

    public synchronized Optional<EventingSnapshot> load() {
        if (!enabled()) {
            return Optional.empty();
        }
        Path path = statePath();
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), EventingSnapshot.class));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось загрузить eventing snapshot", ex);
        }
    }

    private Path statePath() {
        return Path.of(configuration.getEventing().getStatePersistencePath());
    }
}
