package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

class EventingInboxOutboxStorageFactoryTest {

    private final EventingInboxOutboxStorageFactory factory = new EventingInboxOutboxStorageFactory();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldReturnInMemoryStorageForMemoryMode() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().getStorage().setMode("MEMORY");

        EventingInboxOutboxStorage storage = factory.eventingInboxOutboxStorage(
                cfg,
                objectMapper,
                new InMemoryEventingInboxOutboxStorage()
        );

        Assertions.assertInstanceOf(InMemoryEventingInboxOutboxStorage.class, storage);
    }

    @Test
    void shouldReturnFileStorageForFileMode() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getEventing().getStorage().setMode("FILE");

        EventingInboxOutboxStorage storage = factory.eventingInboxOutboxStorage(
                cfg,
                objectMapper,
                new InMemoryEventingInboxOutboxStorage()
        );

        Assertions.assertInstanceOf(FileEventingInboxOutboxStorage.class, storage);
    }
}

