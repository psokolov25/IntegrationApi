package ru.aritmos.integration.programming;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.aritmos.integration.domain.StudioEditorSettingsDto;

import java.nio.file.Path;
import java.util.Map;

class StudioEditorSettingsServiceTest {

    @Test
    void shouldReturnDefaultsWhenSubjectHasNoSavedSettings(@TempDir Path tempDir) {
        StudioEditorSettingsService service = new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"));
        StudioEditorSettingsDto settings = service.get("editor-user");

        Assertions.assertEquals("dark", settings.theme());
        Assertions.assertEquals(14, settings.fontSize());
        Assertions.assertTrue(settings.autoSave());
        Assertions.assertTrue(settings.wordWrap());
    }

    @Test
    void shouldSaveNormalizedSettings(@TempDir Path tempDir) {
        StudioEditorSettingsService service = new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"));
        StudioEditorSettingsDto saved = service.save("editor-user", new StudioEditorSettingsDto(
                "LIGHT",
                16,
                false,
                true,
                "script-101",
                null
        ));

        Assertions.assertEquals("light", saved.theme());
        Assertions.assertEquals(16, saved.fontSize());
        Assertions.assertFalse(saved.autoSave());
        Assertions.assertEquals("script-101", saved.lastScriptId());
        Assertions.assertNotNull(saved.updatedAt());
    }

    @Test
    void shouldRejectUnsupportedTheme(@TempDir Path tempDir) {
        StudioEditorSettingsService service = new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"));
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                service.save("editor-user", new StudioEditorSettingsDto("blue", 14, true, true, "", null)));
    }

    @Test
    void shouldLoadSettingsFromDisk(@TempDir Path tempDir) {
        Path file = tempDir.resolve("editor-settings.json");
        StudioEditorSettingsService writer = new StudioEditorSettingsService(new ObjectMapper(), file);
        writer.save("editor-user", new StudioEditorSettingsDto("contrast", 18, true, false, "script-A", null));

        StudioEditorSettingsService reader = new StudioEditorSettingsService(new ObjectMapper(), file);
        reader.init();
        StudioEditorSettingsDto loaded = reader.get("editor-user");

        Assertions.assertEquals("contrast", loaded.theme());
        Assertions.assertEquals(18, loaded.fontSize());
        Assertions.assertEquals("script-A", loaded.lastScriptId());
    }

    @Test
    void shouldExportAndImportSettings(@TempDir Path tempDir) {
        Path file = tempDir.resolve("editor-settings.json");
        StudioEditorSettingsService source = new StudioEditorSettingsService(new ObjectMapper(), file);
        source.save("alpha", new StudioEditorSettingsDto("dark", 14, true, true, "s-a", null));
        source.save("beta", new StudioEditorSettingsDto("contrast", 18, false, false, "s-b", null));

        Map<String, StudioEditorSettingsDto> exported = source.exportAll();
        Assertions.assertEquals(2, exported.size());

        StudioEditorSettingsService target = new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings-imported.json"));
        Map<String, Object> importResult = target.importAll(exported, true);
        Assertions.assertEquals(2, importResult.get("applied"));
        Assertions.assertEquals(0, importResult.get("skipped"));
        Assertions.assertEquals("contrast", target.get("beta").theme());
    }

    @Test
    void shouldSkipExistingWhenImportWithoutReplace(@TempDir Path tempDir) {
        StudioEditorSettingsService service = new StudioEditorSettingsService(new ObjectMapper(), tempDir.resolve("editor-settings.json"));
        service.save("alpha", new StudioEditorSettingsDto("dark", 14, true, true, "s-1", null));

        Map<String, Object> importResult = service.importAll(Map.of(
                "alpha", new StudioEditorSettingsDto("light", 16, true, true, "s-2", null),
                "beta", new StudioEditorSettingsDto("contrast", 20, false, true, "s-3", null)
        ), false);

        Assertions.assertEquals(1, importResult.get("applied"));
        Assertions.assertEquals(1, importResult.get("skipped"));
        Assertions.assertEquals("dark", service.get("alpha").theme());
        Assertions.assertEquals("contrast", service.get("beta").theme());
    }
}
