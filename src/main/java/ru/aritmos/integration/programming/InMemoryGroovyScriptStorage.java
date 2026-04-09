package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback-хранилище Groovy-скриптов в памяти процесса.
 */
@Singleton
public class InMemoryGroovyScriptStorage implements GroovyScriptStorage {

    private final Map<String, StoredGroovyScript> storage = new ConcurrentHashMap<>();

    @Override
    public void save(StoredGroovyScript script) {
        storage.put(script.scriptId(), script);
    }

    @Override
    public StoredGroovyScript get(String scriptId) {
        return storage.get(scriptId);
    }

    @Override
    public boolean delete(String scriptId) {
        return storage.remove(scriptId) != null;
    }

    @Override
    public List<StoredGroovyScript> list() {
        return storage.values().stream()
                .sorted(Comparator.comparing(StoredGroovyScript::scriptId))
                .toList();
    }
}
