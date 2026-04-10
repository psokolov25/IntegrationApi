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
            case SNAPSHOT_INBOX_OUTBOX -> {
                int limit = intParam(args.get("limit"), 20);
                String status = String.valueOf(args.getOrDefault("status", ""));
                boolean includeSent = booleanParam(args.get("includeSent"), false);
                yield Map.of(
                        "operation", operation.name(),
                        "limit", limit,
                        "status", status,
                        "includeSent", includeSent,
                        "snapshot", studioWorkspaceService.buildInboxOutboxSnapshot(limit, status, includeSent)
                );
            }
            case SNAPSHOT_VISIT_MANAGERS -> Map.of(
                    "operation", operation.name(),
                    "snapshot", studioWorkspaceService.buildVisitManagersSnapshot()
            );
            case SNAPSHOT_BRANCH_CACHE -> {
                int limit = intParam(args.get("limit"), 50);
                yield Map.of(
                        "operation", operation.name(),
                        "limit", limit,
                        "snapshot", studioWorkspaceService.buildBranchStateCacheSnapshot(limit)
                );
            }
            case SNAPSHOT_EXTERNAL_SERVICES -> Map.of(
                    "operation", operation.name(),
                    "snapshot", studioWorkspaceService.buildExternalServicesSnapshot()
            );
            case SNAPSHOT_RUNTIME_SETTINGS -> Map.of(
                    "operation", operation.name(),
                    "snapshot", studioWorkspaceService.buildRuntimeSettingsSnapshot()
            );
            case EXPORT_EDITOR_SETTINGS -> Map.of(
                    "operation", operation.name(),
                    "settingsBySubject", studioEditorSettingsService.exportAll(),
                    "capabilities", studioEditorSettingsService.capabilities()
            );
            case PREVIEW_EVENTING_MAINTENANCE -> Map.of(
                    "operation", operation.name(),
                    "report", eventDispatcherService.previewMaintenance(),
                    "stats", eventDispatcherService.stats()
            );
            case EXPORT_EVENTING_SNAPSHOT -> Map.of(
                    "operation", operation.name(),
                    "snapshot", eventDispatcherService.exportSnapshot(),
                    "health", eventDispatcherService.health()
            );
            case DASHBOARD_SNAPSHOT -> {
                int limit = intParam(args.get("debugHistoryLimit"), 20);
                yield Map.of(
                        "operation", operation.name(),
                        "debugHistoryLimit", limit,
                        "snapshot", studioWorkspaceService.buildDashboardSnapshot(limit)
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

    private boolean booleanParam(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    enum Operation {
        FLUSH_OUTBOX("Повторно отправить pending/failed outbox-сообщения", Map.of("limit", 100)),
        RECOVER_STALE_INBOX("Перевести stale PROCESSING inbox-записи в FAILED", Map.of()),
        CLEAR_DEBUG_HISTORY("Очистить debug history (весь или по scriptId)", Map.of("scriptId", "")),
        REFRESH_BOOTSTRAP("Получить свежий studio bootstrap snapshot", Map.of("debugHistoryLimit", 20)),
        SNAPSHOT_INBOX_OUTBOX("Получить диагностический срез inbox/outbox для IDE", Map.of("limit", 20, "status", "", "includeSent", false)),
        SNAPSHOT_VISIT_MANAGERS("Получить диагностический срез конфигурации VisitManager", Map.of()),
        SNAPSHOT_BRANCH_CACHE("Получить диагностический срез кэша отделений branch-state", Map.of("limit", 50)),
        SNAPSHOT_EXTERNAL_SERVICES("Получить диагностический срез внешних сервисов и брокеров", Map.of()),
        SNAPSHOT_RUNTIME_SETTINGS("Получить runtime-срез настроек контрольной панели", Map.of()),
        EXPORT_EDITOR_SETTINGS("Экспортировать настройки IDE-редактора для GUI backup", Map.of()),
        PREVIEW_EVENTING_MAINTENANCE("Предпросмотр maintenance inbox/outbox/DLQ/processed без изменений", Map.of()),
        EXPORT_EVENTING_SNAPSHOT("Экспортировать snapshot eventing для backup/import", Map.of()),
        DASHBOARD_SNAPSHOT("Получить сводный dashboard snapshot для GUI", Map.of("debugHistoryLimit", 20));

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
