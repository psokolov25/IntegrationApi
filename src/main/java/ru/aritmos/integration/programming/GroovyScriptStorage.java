package ru.aritmos.integration.programming;

import java.util.List;

/**
 * Хранилище Groovy-скриптов programmable API.
 */
public interface GroovyScriptStorage {

    void save(StoredGroovyScript script);

    StoredGroovyScript get(String scriptId);

    boolean delete(String scriptId);

    List<StoredGroovyScript> list();
}
