package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

class FileGroovyScriptStorageTest {

    @Test
    void shouldPersistScriptsBetweenStorageInstances() throws Exception {
        Path dir = Files.createTempDirectory("script-storage-test");
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        FileGroovyScriptStorage first = new FileGroovyScriptStorage(dir, mapper);
        first.save(new StoredGroovyScript(
                "persisted-script",
                GroovyScriptType.BRANCH_CACHE_QUERY,
                "return [ok: true]",
                "test",
                Instant.now(),
                "tester"
        ));

        FileGroovyScriptStorage second = new FileGroovyScriptStorage(dir, mapper);
        StoredGroovyScript loaded = second.get("persisted-script");

        Assertions.assertNotNull(loaded);
        Assertions.assertEquals("persisted-script", loaded.scriptId());
        Assertions.assertEquals("return [ok: true]", loaded.scriptBody());
        Assertions.assertEquals(1, second.list().size());
    }
}
