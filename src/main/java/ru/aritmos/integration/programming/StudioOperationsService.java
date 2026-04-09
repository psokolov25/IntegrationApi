package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;
import ru.aritmos.integration.domain.StudioOperationCatalogItemDto;
import ru.aritmos.integration.eventing.EventDispatcherService;

import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Оркестратор служебных операций programmable-студии для GUI/IDE.
 */
@Singleton
public class StudioOperationsService {

    private final EventDispatcherService eventDispatcherService;
    private final ScriptDebugHistoryService scriptDebugHistoryService;
    private final StudioWorkspaceService studioWorkspaceService;
    private final StudioEditorSettingsService studioEditorSettingsService;

    public StudioOperationsService(EventDispatcherService eventDispatcherService,
                                   ScriptDebugHistoryService scriptDebugHistoryService,
                                   StudioWorkspaceService studioWorkspaceService,
                                   StudioEditorSettingsService studioEditorSettingsService) {
        this.eventDispatcherService = eventDispatcherService;
        this.scriptDebugHistoryService = scriptDebugHistoryService;
        this.studioWorkspaceService = studioWorkspaceService;
        this.studioEditorSettingsService = studioEditorSettingsService;
    }

    public Map<String, Object> execute(String operationRaw, Map<String, Object> parameters, String subjectId) {
        if (operationRaw == null || operationRaw.isBlank()) {
            throw new IllegalArgumentException("operation обязателен");
        }
        Map<String, Object> args = parameters == null ? Map.of() : parameters;
        Operation operation = Operation.from(operationRaw);
        return switch (operation) {
            case FLUSH_OUTBOX -> {
                int limit = intParam(args.get("limit"), 100);
                yield Map.of(
                        "operation", operation.name(),
                        "limit", limit,
                        "results", eventDispatcherService.flushOutbox(limit)
                );
            }
            case RECOVER_STALE_INBOX -> {
                int recovered = eventDispatcherService.recoverStaleInboxProcessing();
                yield Map.of(
                        "operation", operation.name(),
                        "recovered", recovered
                );
            }
            case CLEAR_DEBUG_HISTORY -> {
                String scriptId = String.valueOf(args.getOrDefault("scriptId", ""));
                int removed = scriptDebugHistoryService.clear(scriptId);
                yield Map.of(
                        "operation", operation.name(),
                        "removed", removed,
                        "scriptId", scriptId
                );
            }
            case REFRESH_BOOTSTRAP -> {
                int limit = intParam(args.get("debugHistoryLimit"), 20);
                Map<String, Object> snapshot = new LinkedHashMap<>(studioWorkspaceService.buildWorkspaceSnapshot(limit));
                snapshot.put("editorSettings", studioEditorSettingsService.get(subjectId));
                snapshot.put("editorCapabilities", studioEditorSettingsService.capabilities());
                yield Map.of(
                        "operation", operation.name(),
                        "snapshot", snapshot
                );
            }
        };
    }

    public List<StudioOperationCatalogItemDto> catalog() {
        return Arrays.stream(Operation.values())
                .map(operation -> new StudioOperationCatalogItemDto(
                        operation.name(),
                        operation.description(),
                        operation.parameterTemplate()))
                .toList();
    }

    private int intParam(Object value, int fallback) {
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

    enum Operation {
        FLUSH_OUTBOX("Повторно отправить pending/failed outbox-сообщения", Map.of("limit", 100)),
        RECOVER_STALE_INBOX("Перевести stale PROCESSING inbox-записи в FAILED", Map.of()),
        CLEAR_DEBUG_HISTORY("Очистить debug history (весь или по scriptId)", Map.of("scriptId", "")),
        REFRESH_BOOTSTRAP("Получить свежий studio bootstrap snapshot", Map.of("debugHistoryLimit", 20));

        private final String description;
        private final Map<String, Object> parameterTemplate;

        Operation(String description, Map<String, Object> parameterTemplate) {
            this.description = description;
            this.parameterTemplate = parameterTemplate;
        }

        String description() {
            return description;
        }

        Map<String, Object> parameterTemplate() {
            return parameterTemplate;
        }

        static Operation from(String raw) {
            try {
                return Operation.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Неподдерживаемая operation: " + raw);
            }
        }
    }
}
