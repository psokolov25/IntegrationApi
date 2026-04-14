package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Файловое хранилище inbox/outbox для восстановления после пересборки контейнера.
 */
public class FileEventingInboxOutboxStorage implements EventingInboxOutboxStorage {

    private static final TypeReference<Map<String, EventInboxService.InboxEntry>> INBOX_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, EventOutboxMessage>> OUTBOX_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path inboxPath;
    private final Path outboxPath;
    private final Path runtimeSettingsPath;

    public FileEventingInboxOutboxStorage(Path storageDir, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.inboxPath = storageDir.resolve("inbox.json");
        this.outboxPath = storageDir.resolve("outbox.json");
        this.runtimeSettingsPath = storageDir.resolve("runtime-settings.json");
        ensureDirectory(storageDir);
    }

    @Override
    public synchronized Map<String, EventInboxService.InboxEntry> loadInbox() {
        return read(inboxPath, INBOX_TYPE);
    }

    @Override
    public synchronized void saveInbox(Map<String, EventInboxService.InboxEntry> snapshot) {
        write(inboxPath, snapshot);
    }

    @Override
    public synchronized Map<String, EventOutboxMessage> loadOutbox() {
        return read(outboxPath, OUTBOX_TYPE);
    }

    @Override
    public synchronized void saveOutbox(Map<String, EventOutboxMessage> snapshot) {
        write(outboxPath, snapshot);
    }

    @Override
    public synchronized Map<String, Object> loadRuntimeSettings() {
        return read(runtimeSettingsPath, new TypeReference<Map<String, Object>>() {
        });
    }

    @Override
    public synchronized void saveRuntimeSettings(Map<String, Object> snapshot) {
        write(runtimeSettingsPath, snapshot);
    }

    private <T> Map<String, T> read(Path path, TypeReference<Map<String, T>> type) {
        if (!Files.exists(path)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(path.toFile(), type);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось прочитать snapshot inbox/outbox из файла: " + path, ex);
        }
    }

    private void write(Path path, Object payload) {
        try {
            objectMapper.writeValue(path.toFile(), payload == null ? Map.of() : payload);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось сохранить snapshot inbox/outbox в файл: " + path, ex);
        }
    }

    private void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось создать директорию storage для inbox/outbox: " + dir, ex);
        }
    }
}
