package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Файловое хранилище Groovy-скриптов с сохранением между перезапусками контейнера.
 */
public class FileGroovyScriptStorage implements GroovyScriptStorage {

    private final Path storageDir;
    private final ObjectMapper objectMapper;

    public FileGroovyScriptStorage(Path storageDir, ObjectMapper objectMapper) {
        this.storageDir = storageDir;
        this.objectMapper = objectMapper;
        ensureStorageDir();
    }

    @Override
    public synchronized void save(StoredGroovyScript script) {
        write(scriptPath(script.scriptId()), script);
    }

    @Override
    public synchronized StoredGroovyScript get(String scriptId) {
        Path path = scriptPath(scriptId);
        if (!Files.exists(path)) {
            return null;
        }
        return read(path);
    }

    @Override
    public synchronized boolean delete(String scriptId) {
        Path path = scriptPath(scriptId);
        try {
            return Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось удалить Groovy-скрипт из файлового хранилища", ex);
        }
    }

    @Override
    public synchronized List<StoredGroovyScript> list() {
        ensureStorageDir();
        try (Stream<Path> stream = Files.list(storageDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::read)
                    .sorted(Comparator.comparing(StoredGroovyScript::scriptId))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось прочитать файловое хранилище Groovy-скриптов", ex);
        }
    }

    private Path scriptPath(String scriptId) {
        return storageDir.resolve(scriptId + ".json");
    }

    private void write(Path path, StoredGroovyScript script) {
        try {
            objectMapper.writeValue(path.toFile(), script);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось записать Groovy-скрипт в файловое хранилище", ex);
        }
    }

    private StoredGroovyScript read(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), StoredGroovyScript.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось прочитать Groovy-скрипт из файлового хранилища", ex);
        }
    }

    private void ensureStorageDir() {
        try {
            Files.createDirectories(storageDir);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось создать директорию файлового хранилища: " + storageDir, ex);
        }
    }
}
