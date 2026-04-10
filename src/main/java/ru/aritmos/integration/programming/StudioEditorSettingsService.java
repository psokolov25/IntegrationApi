package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.StudioEditorSettingsDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory хранение персональных настроек IDE-редактора programmable-студии.
 */
@Singleton
public class StudioEditorSettingsService {

    private static final StudioEditorSettingsDto DEFAULT_SETTINGS = new StudioEditorSettingsDto(
            "dark",
            14,
            true,
            true,
            "",
            Instant.EPOCH
    );

    private final Map<String, StudioEditorSettingsDto> settingsBySubject = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Path persistencePath;

    public StudioEditorSettingsService(IntegrationGatewayConfiguration configuration,
                                       ObjectMapper objectMapper) {
        this(objectMapper, resolveDefaultPersistencePath(configuration));
    }

    StudioEditorSettingsService(ObjectMapper objectMapper, Path persistencePath) {
        this.objectMapper = objectMapper;
        this.persistencePath = persistencePath;
    }

    @PostConstruct
    void init() {
        loadFromDisk();
    }

    public StudioEditorSettingsDto get(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return withTimestamp(DEFAULT_SETTINGS, Instant.now());
        }
        StudioEditorSettingsDto current = settingsBySubject.get(subjectId);
        if (current == null) {
            return withTimestamp(DEFAULT_SETTINGS, Instant.now());
        }
        return current;
    }

    public StudioEditorSettingsDto save(String subjectId, StudioEditorSettingsDto request) {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId обязателен");
        }
        if (request == null) {
            throw new IllegalArgumentException("payload настроек обязателен");
        }
        StudioEditorSettingsDto normalized = normalizeSettings(request);
        settingsBySubject.put(subjectId, normalized);
        persistToDisk();
        return normalized;
    }

    /**
     * Экспортирует все сохраненные настройки IDE для GUI backup/restore.
     */
    public Map<String, StudioEditorSettingsDto> exportAll() {
        return settingsBySubject.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    /**
     * Импортирует настройки IDE из GUI backup (c merge/replace режимом).
     */
    public Map<String, Object> importAll(Map<String, StudioEditorSettingsDto> imported, boolean replaceExisting) {
        if (imported == null || imported.isEmpty()) {
            return Map.of(
                    "received", 0,
                    "applied", 0,
                    "skipped", 0,
                    "replaceExisting", replaceExisting
            );
        }
        int applied = 0;
        int skipped = 0;
        for (Map.Entry<String, StudioEditorSettingsDto> entry : imported.entrySet()) {
            String subjectId = entry.getKey();
            if (subjectId == null || subjectId.isBlank() || entry.getValue() == null) {
                skipped++;
                continue;
            }
            if (!replaceExisting && settingsBySubject.containsKey(subjectId)) {
                skipped++;
                continue;
            }
            settingsBySubject.put(subjectId, normalizeSettings(entry.getValue()));
            applied++;
        }
        if (applied > 0) {
            persistToDisk();
        }
        return Map.of(
                "received", imported.size(),
                "applied", applied,
                "skipped", skipped,
                "replaceExisting", replaceExisting
        );
    }

    public Map<String, Object> capabilities() {
        return Map.of(
                "themes", List.of("dark", "light", "contrast"),
                "fontSizeMin", 10,
                "fontSizeMax", 28,
                "storage", "file",
                "persistencePath", persistencePath.toString(),
                "importExportSupported", true
        );
    }

    private StudioEditorSettingsDto normalizeSettings(StudioEditorSettingsDto request) {
        String normalizedTheme = normalizeTheme(request.theme());
        int normalizedFontSize = normalizeFontSize(request.fontSize());
        return new StudioEditorSettingsDto(
                normalizedTheme,
                normalizedFontSize,
                request.autoSave(),
                request.wordWrap(),
                request.lastScriptId() == null ? "" : request.lastScriptId().trim(),
                Instant.now()
        );
    }

    private StudioEditorSettingsDto withTimestamp(StudioEditorSettingsDto base, Instant timestamp) {
        return new StudioEditorSettingsDto(
                base.theme(),
                base.fontSize(),
                base.autoSave(),
                base.wordWrap(),
                base.lastScriptId(),
                timestamp
        );
    }

    private String normalizeTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            return "dark";
        }
        String normalized = theme.trim().toLowerCase();
        return switch (normalized) {
            case "dark", "light", "contrast" -> normalized;
            default -> throw new IllegalArgumentException("theme должен быть dark/light/contrast");
        };
    }

    private int normalizeFontSize(int fontSize) {
        if (fontSize <= 0) {
            return 14;
        }
        if (fontSize < 10 || fontSize > 28) {
            throw new IllegalArgumentException("fontSize должен быть в диапазоне 10..28");
        }
        return fontSize;
    }

    private static Path resolveDefaultPersistencePath(IntegrationGatewayConfiguration configuration) {
        String basePath = configuration.getProgrammableApi().getScriptStorage().getFile().getPath();
        Path base = Path.of(basePath == null || basePath.isBlank() ? "cache/program-scripts" : basePath);
        return base.resolve("editor-settings.json");
    }

    private void loadFromDisk() {
        if (persistencePath == null || !Files.exists(persistencePath)) {
            return;
        }
        try {
            Map<String, Map<String, Object>> loaded = objectMapper.readValue(Files.readString(persistencePath), Map.class);
            if (loaded != null) {
                loaded.forEach((subject, raw) -> {
                    if (subject == null || subject.isBlank() || raw == null) {
                        return;
                    }
                    String theme = Objects.toString(raw.get("theme"), "dark");
                    int fontSize = toInt(raw.get("fontSize"), 14);
                    boolean autoSave = toBoolean(raw.get("autoSave"), true);
                    boolean wordWrap = toBoolean(raw.get("wordWrap"), true);
                    String lastScriptId = Objects.toString(raw.get("lastScriptId"), "");
                    Instant updatedAt = parseInstant(raw.get("updatedAt"));
                    settingsBySubject.put(subject, new StudioEditorSettingsDto(
                            normalizeTheme(theme),
                            normalizeFontSize(fontSize),
                            autoSave,
                            wordWrap,
                            lastScriptId,
                            updatedAt
                    ));
                });
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось загрузить настройки IDE-редактора", ex);
        }
    }

    private void persistToDisk() {
        if (persistencePath == null) {
            return;
        }
        try {
            Path parent = persistencePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Map<String, Object>> serializable = new LinkedHashMap<>();
            settingsBySubject.forEach((subject, settings) -> serializable.put(subject, Map.of(
                    "theme", settings.theme(),
                    "fontSize", settings.fontSize(),
                    "autoSave", settings.autoSave(),
                    "wordWrap", settings.wordWrap(),
                    "lastScriptId", settings.lastScriptId(),
                    "updatedAt", settings.updatedAt() == null ? "" : settings.updatedAt().toString()
            )));
            Files.writeString(persistencePath, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(serializable));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось сохранить настройки IDE-редактора", ex);
        }
    }

    private int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean toBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return Instant.now();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(text);
        } catch (Exception ex) {
            return Instant.now();
        }
    }
}
