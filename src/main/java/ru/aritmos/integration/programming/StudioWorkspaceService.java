package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.EventInboxService;
import ru.aritmos.integration.eventing.EventOutboxService;
import ru.aritmos.integration.service.BranchStateCache;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сводный snapshot среды programmable-студии для GUI (editor/settings/connectors/inbox-outbox).
 */
@Singleton
public class StudioWorkspaceService {

    private final IntegrationGatewayConfiguration configuration;
    private final EventInboxService inboxService;
    private final EventOutboxService outboxService;
    private final GroovyScriptStorage scriptStorage;
    private final ScriptDebugHistoryService debugHistoryService;
    private final List<CustomerMessageBusAdapter> messageBusAdapters;
    private final BranchStateCache branchStateCache;

    public StudioWorkspaceService(IntegrationGatewayConfiguration configuration,
                                  EventInboxService inboxService,
                                  EventOutboxService outboxService,
                                  GroovyScriptStorage scriptStorage,
                                  ScriptDebugHistoryService debugHistoryService,
                                  List<CustomerMessageBusAdapter> messageBusAdapters) {
        this(configuration, inboxService, outboxService, scriptStorage, debugHistoryService, messageBusAdapters, null);
    }

    public StudioWorkspaceService(IntegrationGatewayConfiguration configuration,
                                  EventInboxService inboxService,
                                  EventOutboxService outboxService,
                                  GroovyScriptStorage scriptStorage,
                                  ScriptDebugHistoryService debugHistoryService,
                                  List<CustomerMessageBusAdapter> messageBusAdapters,
                                  BranchStateCache branchStateCache) {
        this.configuration = configuration;
        this.inboxService = inboxService;
        this.outboxService = outboxService;
        this.scriptStorage = scriptStorage;
        this.debugHistoryService = debugHistoryService;
        this.messageBusAdapters = messageBusAdapters;
        this.branchStateCache = branchStateCache;
    }

    public Map<String, Object> buildWorkspaceSnapshot(int debugHistoryLimit) {
        int limit = debugHistoryLimit <= 0 ? 20 : Math.min(debugHistoryLimit, 200);
        int eventPreviewLimit = Math.min(limit, 20);
        List<StoredGroovyScript> scripts = scriptStorage.list();
        List<ScriptDebugHistoryService.DebugEntry> debugHistory = debugHistoryService.list("", limit);
        List<String> supportedBrokerTypes = supportedBrokerTypes();
        Set<String> configuredBrokerTypes = configuration.getProgrammableApi().getMessageBrokers().stream()
                .map(IntegrationGatewayConfiguration.MessageBrokerSettings::getType)
                .filter(type -> type != null && !type.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> unsupportedBrokerTypes = configuredBrokerTypes.stream()
                .filter(type -> supportedBrokerTypes.stream().noneMatch(item -> item.equalsIgnoreCase(type)))
                .sorted()
                .toList();

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("engine", "GroovyShell");
        runtime.put("scriptStorage", Map.of(
                "redisEnabled", configuration.getProgrammableApi().getScriptStorage().getRedis().isEnabled(),
                "fileEnabled", configuration.getProgrammableApi().getScriptStorage().getFile().isEnabled(),
                "filePath", configuration.getProgrammableApi().getScriptStorage().getFile().getPath()
        ));
        runtime.put("scriptCount", scripts.size());
        runtime.put("scriptTypes", scripts.stream()
                .collect(Collectors.groupingBy(script -> script.type().name(), LinkedHashMap::new, Collectors.counting())));

        Map<String, Object> connectors = new LinkedHashMap<>();
        connectors.put("restServices", configuration.getProgrammableApi().getExternalRestServices().size());
        connectors.put("messageBrokers", configuration.getProgrammableApi().getMessageBrokers().size());
        connectors.put("messageReactions", configuration.getProgrammableApi().getMessageReactions().size());
        connectors.put("supportedBrokerTypes", supportedBrokerTypes);
        connectors.put("configuredBrokerTypes", configuredBrokerTypes.stream().sorted().toList());
        connectors.put("unsupportedBrokerTypes", unsupportedBrokerTypes);

        Map<String, Object> eventing = new LinkedHashMap<>();
        eventing.put("enabled", configuration.getEventing().isEnabled());
        eventing.put("inbox", Map.of(
                "size", inboxService.size(),
                "processing", inboxService.processingSize(),
                "recent", inboxService.snapshot(eventPreviewLimit, "")
                        .stream()
                        .map(item -> Map.of(
                                "eventId", item.eventId(),
                                "status", item.status(),
                                "attempts", item.attempts(),
                                "updatedAt", item.updatedAt() == null ? "" : item.updatedAt().toString()
                        ))
                        .toList()
        ));
        eventing.put("outbox", Map.of(
                "size", outboxService.size(),
                "pending", outboxService.pendingSize(),
                "failed", outboxService.failedSize(),
                "dead", outboxService.deadSize(),
                "recent", outboxService.snapshot(eventPreviewLimit, "", true)
                        .stream()
                        .map(item -> Map.of(
                                "eventId", item.eventId(),
                                "status", item.status(),
                                "attempts", item.attempts(),
                                "updatedAt", item.updatedAt() == null ? "" : item.updatedAt().toString()
                        ))
                        .toList()
        ));

        Map<String, Object> ide = new LinkedHashMap<>();
        ide.put("availableScriptTypes", List.of(
                GroovyScriptType.BRANCH_CACHE_QUERY.name(),
                GroovyScriptType.VISIT_MANAGER_ACTION.name(),
                GroovyScriptType.MESSAGE_BUS_REACTION.name()
        ));
        ide.put("debugHistorySize", debugHistory.size());
        ide.put("debugHistoryLimit", limit);
        ide.put("debugHistoryRecent", debugHistory.stream()
                .limit(Math.min(10, limit))
                .map(entry -> Map.of(
                        "scriptId", entry.scriptId(),
                        "ok", entry.ok(),
                        "durationMs", entry.durationMs(),
                        "startedAt", entry.startedAt() == null ? "" : entry.startedAt().toString()
                ))
                .toList());
        ide.put("latestDebugAt", debugHistory.stream()
                .map(ScriptDebugHistoryService.DebugEntry::startedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .map(Instant::toString)
                .orElse(""));

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("securityMode", configuration.getSecurityMode().name());
        settings.put("programmableApiEnabled", configuration.getProgrammableApi().isEnabled());
        settings.put("anonymousAccessEnabled", configuration.getAnonymousAccess().isEnabled());
        settings.put("branchStateEventRefreshDebounce", configuration.getBranchStateEventRefreshDebounce().toString());
        settings.put("eventingOutboxBackoffSeconds", configuration.getEventing().getOutboxBackoffSeconds());
        settings.put("eventingOutboxMaxAttempts", configuration.getEventing().getOutboxMaxAttempts());

        Map<String, Object> gui = new LinkedHashMap<>();
        gui.put("tabs", List.of("scripts", "debug", "connectors", "inbox-outbox", "settings", "templates"));
        gui.put("ideReady", true);
        gui.put("warnings", buildWarnings(unsupportedBrokerTypes, outboxService.deadSize(), inboxService.processingSize()));
        gui.put("actions", List.of(
                Map.of("id", "studio.bootstrap", "method", "GET", "path", "/api/v1/program/studio/bootstrap"),
                Map.of("id", "studio.settings.get", "method", "GET", "path", "/api/v1/program/studio/settings"),
                Map.of("id", "studio.settings.put", "method", "PUT", "path", "/api/v1/program/studio/settings"),
                Map.of("id", "studio.settings.export", "method", "GET", "path", "/api/v1/program/studio/settings/export"),
                Map.of("id", "studio.settings.import", "method", "POST", "path", "/api/v1/program/studio/settings/import"),
                Map.of("id", "studio.capabilities", "method", "GET", "path", "/api/v1/program/studio/capabilities"),
                Map.of("id", "studio.operations", "method", "POST", "path", "/api/v1/program/studio/operations"),
                Map.of("id", "studio.dashboard.snapshot", "method", "POST", "path", "/api/v1/program/studio/operations {\"operation\":\"DASHBOARD_SNAPSHOT\",\"parameters\":{\"debugHistoryLimit\":20}}"),
                Map.of("id", "templates.import.preview", "method", "POST", "path", "/api/v1/program/templates/import/preview"),
                Map.of("id", "templates.import", "method", "POST", "path", "/api/v1/program/templates/import"),
                Map.of("id", "templates.export", "method", "POST", "path", "/api/v1/program/templates/export"),
                Map.of("id", "connectors.health", "method", "GET", "path", "/api/v1/program/connectors/health"),
                Map.of("id", "events.outbox.flush", "method", "POST", "path", "/api/v1/events/outbox/flush?limit=100"),
                Map.of("id", "events.inbox.recover", "method", "POST", "path", "/api/v1/events/inbox/recover-stale"),
                Map.of("id", "events.snapshot.export", "method", "GET", "path", "/api/v1/events/export"),
                Map.of("id", "events.snapshot.import.preview", "method", "POST", "path", "/api/v1/events/import/preview"),
                Map.of("id", "events.snapshot.import", "method", "POST", "path", "/api/v1/events/import")
        ));

        return Map.of(
                "runtime", runtime,
                "connectors", connectors,
                "eventing", eventing,
                "ide", ide,
                "settings", settings,
                "gui", gui,
                "generatedAt", Instant.now().toString()
        );
    }

    /**
     * Сводный dashboard snapshot для GUI: IDE/runtime + inbox/outbox + VisitManager + external services.
     */
    public Map<String, Object> buildDashboardSnapshot(int debugHistoryLimit) {
        int safeLimit = debugHistoryLimit <= 0 ? 20 : Math.min(debugHistoryLimit, 200);
        Map<String, Object> workspace = new LinkedHashMap<>(buildWorkspaceSnapshot(safeLimit));
        return Map.of(
                "workspace", workspace,
                "inboxOutbox", buildInboxOutboxSnapshot(Math.min(20, safeLimit), "", true),
                "visitManagers", buildVisitManagersSnapshot(),
                "branchStateCache", buildBranchStateCacheSnapshot(Math.min(50, safeLimit)),
                "externalServices", buildExternalServicesSnapshot(),
                "runtimeSettings", buildRuntimeSettingsSnapshot(),
                "generatedAt", Instant.now().toString()
        );
    }

    /**
     * Диагностический срез branch-state кэша (по данным DataBus/VisitManager синхронизации).
     */
    public Map<String, Object> buildBranchStateCacheSnapshot(int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 200);
        if (branchStateCache == null) {
            return Map.of(
                    "enabled", false,
                    "reason", "BranchStateCache не подключен к StudioWorkspaceService",
                    "generatedAt", Instant.now().toString()
            );
        }
        List<Map<String, Object>> recent = branchStateCache.snapshot().stream()
                .limit(safeLimit)
                .map(state -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("branchId", state.branchId() == null ? "" : state.branchId());
                    item.put("visitManagerId", state.sourceVisitManagerId() == null ? "" : state.sourceVisitManagerId());
                    item.put("status", state.status() == null ? "" : state.status());
                    item.put("queueSize", state.queueSize());
                    item.put("updatedAt", state.updatedAt() == null ? "" : state.updatedAt().toString());
                    return item;
                })
                .toList();

        Map<String, Long> byVisitManager = branchStateCache.snapshot().stream()
                .collect(Collectors.groupingBy(item -> item.sourceVisitManagerId() == null ? "" : item.sourceVisitManagerId(),
                        LinkedHashMap::new, Collectors.counting()));

        return Map.of(
                "enabled", true,
                "total", branchStateCache.snapshot().size(),
                "limit", safeLimit,
                "byVisitManager", byVisitManager,
                "recent", recent,
                "generatedAt", Instant.now().toString()
        );
    }

    /**
     * Диагностический срез inbox/outbox для операционных действий IDE.
     */
    public Map<String, Object> buildInboxOutboxSnapshot(int limit, String status, boolean includeSent) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 200);
        String statusFilter = status == null ? "" : status.trim();
        Map<String, Object> inbox = new LinkedHashMap<>();
        inbox.put("size", inboxService.size());
        inbox.put("processing", inboxService.processingSize());
        inbox.put("statusFilter", statusFilter);
        inbox.put("recent", inboxService.snapshot(safeLimit, statusFilter));

        Map<String, Object> outbox = new LinkedHashMap<>();
        outbox.put("size", outboxService.size());
        outbox.put("pending", outboxService.pendingSize());
        outbox.put("failed", outboxService.failedSize());
        outbox.put("dead", outboxService.deadSize());
        outbox.put("statusFilter", statusFilter);
        outbox.put("includeSent", includeSent);
        outbox.put("recent", outboxService.snapshot(safeLimit, statusFilter, includeSent));

        return Map.of(
                "inbox", inbox,
                "outbox", outbox,
                "generatedAt", Instant.now().toString()
        );
    }

    /**
     * Диагностический срез интеграции с VisitManager (конфиг и маршрутизация).
     */
    public Map<String, Object> buildVisitManagersSnapshot() {
        List<Map<String, Object>> visitManagers = configuration.getVisitManagers().stream()
                .map(vm -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", vm.getId());
                    item.put("baseUrl", vm.getBaseUrl());
                    item.put("regionId", vm.getRegionId());
                    item.put("active", vm.isActive());
                    return item;
                })
                .toList();
        long activeCount = configuration.getVisitManagers().stream()
                .filter(IntegrationGatewayConfiguration.VisitManagerInstance::isActive)
                .count();
        return Map.of(
                "visitManagers", visitManagers,
                "visitManagersCount", configuration.getVisitManagers().size(),
                "activeVisitManagersCount", activeCount,
                "branchRoutingCount", configuration.getBranchRouting().size(),
                "fallbackRoutingCount", configuration.getBranchFallbackRouting().size(),
                "aggregateMaxBranches", configuration.getAggregateMaxBranches(),
                "aggregateRequestTimeoutMillis", configuration.getAggregateRequestTimeoutMillis(),
                "generatedAt", Instant.now().toString()
        );
    }

    /**
     * Диагностический срез внешних сервисов и брокеров programmable API.
     */
    public Map<String, Object> buildExternalServicesSnapshot() {
        List<Map<String, Object>> restServices = configuration.getProgrammableApi().getExternalRestServices().stream()
                .map(item -> {
                    Map<String, Object> service = new LinkedHashMap<>();
                    service.put("id", item.getId());
                    service.put("baseUrl", item.getBaseUrl());
                    service.put("defaultHeaders", item.getDefaultHeaders());
                    return service;
                })
                .toList();
        List<Map<String, Object>> brokers = configuration.getProgrammableApi().getMessageBrokers().stream()
                .map(item -> {
                    Map<String, Object> broker = new LinkedHashMap<>();
                    broker.put("id", item.getId());
                    broker.put("type", item.getType());
                    broker.put("enabled", item.isEnabled());
                    return broker;
                })
                .toList();

        return Map.of(
                "restServices", restServices,
                "restServicesCount", restServices.size(),
                "messageBrokers", brokers,
                "messageBrokersCount", brokers.size(),
                "messageReactionsCount", configuration.getProgrammableApi().getMessageReactions().size(),
                "generatedAt", Instant.now().toString()
        );
    }

    /**
     * Сводный runtime-срез настроек контрольной панели (без секретов), доступный для GUI.
     */
    public Map<String, Object> buildRuntimeSettingsSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("securityMode", configuration.getSecurityMode().name());
        snapshot.put("anonymousAccessEnabled", configuration.getAnonymousAccess().isEnabled());
        snapshot.put("programmableApiEnabled", configuration.getProgrammableApi().isEnabled());
        snapshot.put("eventingEnabled", configuration.getEventing().isEnabled());
        snapshot.put("outboxBackoffSeconds", configuration.getEventing().getOutboxBackoffSeconds());
        snapshot.put("outboxMaxAttempts", configuration.getEventing().getOutboxMaxAttempts());
        snapshot.put("inboxProcessingTimeoutSeconds", configuration.getEventing().getInboxProcessingTimeoutSeconds());
        snapshot.put("branchStateCacheTtl", configuration.getBranchStateCacheTtl().toString());
        snapshot.put("branchStateEventRefreshDebounce", configuration.getBranchStateEventRefreshDebounce().toString());
        snapshot.put("aggregateMaxBranches", configuration.getAggregateMaxBranches());
        snapshot.put("aggregateRequestTimeoutMillis", configuration.getAggregateRequestTimeoutMillis());
        snapshot.put("generatedAt", Instant.now().toString());
        return snapshot;
    }

    /**
     * Операционный playbook для GUI/IDE: последовательность проверок по всем ключевым группам.
     */
    public List<Map<String, Object>> buildPlaybook() {
        return List.of(
                Map.of(
                        "order", 1,
                        "group", "inbox-outbox",
                        "title", "Проверить backlog eventing",
                        "check", "Сверить inbox.processing и outbox.failed/dead в studio bootstrap",
                        "api", "GET /api/v1/program/studio/bootstrap?debugHistoryLimit=20"
                ),
                Map.of(
                        "order", 2,
                        "group", "groovy-runtime",
                        "title", "Проверить runtime Groovy и storage",
                        "check", "Убедиться, что runtime.scriptStorage и scriptCount соответствуют ожидаемым",
                        "api", "GET /api/v1/program/studio/bootstrap?debugHistoryLimit=20"
                ),
                Map.of(
                        "order", 3,
                        "group", "connectors",
                        "title", "Проверить коннекторы и типы брокеров",
                        "check", "Проверить unsupportedBrokerTypes и connectors health",
                        "api", "GET /api/v1/program/connectors/health"
                ),
                Map.of(
                        "order", 4,
                        "group", "branch-cache",
                        "title", "Проверить актуальность branch-state кэша",
                        "check", "Сверить total/byVisitManager/recent в branch-state snapshot и наличие свежих updatedAt",
                        "api", "POST /api/v1/program/studio/operations {\"operation\":\"SNAPSHOT_BRANCH_CACHE\",\"parameters\":{\"limit\":50}}"
                ),
                Map.of(
                        "order", 5,
                        "group", "ide-editor",
                        "title", "Проверить IDE-историю и настройки",
                        "check", "Сверить debugHistoryRecent + editorSettings/editorCapabilities",
                        "api", "GET /api/v1/program/studio/bootstrap?debugHistoryLimit=20"
                ),
                Map.of(
                        "order", 6,
                        "group", "settings",
                        "title", "Проверить и при необходимости обновить настройки редактора",
                        "check", "Проверить theme/fontSize и при необходимости выполнить backup/restore настроек",
                        "api", "GET|PUT /api/v1/program/studio/settings, GET /api/v1/program/studio/settings/export, POST /api/v1/program/studio/settings/import"
                ),
                Map.of(
                        "order", 7,
                        "group", "runtime-settings",
                        "title", "Проверить runtime-настройки контрольной панели",
                        "check", "Сверить eventing/aggregation/branch-cache параметры в runtimeSettings snapshot",
                        "api", "POST /api/v1/program/studio/operations {\"operation\":\"SNAPSHOT_RUNTIME_SETTINGS\"}"
                ),
                Map.of(
                        "order", 8,
                        "group", "import-export",
                        "title", "Проверить импорт/экспорт integration templates",
                        "check", "Выполнить preview/import/export ITS архива через GUI workflow",
                        "api", "POST /api/v1/program/templates/import/preview, POST /api/v1/program/templates/import, POST /api/v1/program/templates/export"
                ),
                Map.of(
                        "order", 9,
                        "group", "gui-ops",
                        "title", "Выполнить операционные действия",
                        "check", "Запустить FLUSH_OUTBOX / RECOVER_STALE_INBOX / SNAPSHOT_BRANCH_CACHE / SNAPSHOT_RUNTIME_SETTINGS / CLEAR_DEBUG_HISTORY / EXPORT_EDITOR_SETTINGS / PREVIEW_EVENTING_MAINTENANCE / EXPORT_EVENTING_SNAPSHOT",
                        "api", "POST /api/v1/program/studio/operations"
                )
        );
    }

    private List<String> buildWarnings(List<String> unsupportedBrokerTypes, int deadOutbox, int processingInbox) {
        List<String> warnings = new java.util.ArrayList<>();
        if (!unsupportedBrokerTypes.isEmpty()) {
            warnings.add("Обнаружены неподдерживаемые типы брокеров: " + String.join(", ", unsupportedBrokerTypes));
        }
        if (deadOutbox > 0) {
            warnings.add("В outbox есть DEAD-события: " + deadOutbox);
        }
        if (processingInbox > 0) {
            warnings.add("В inbox есть PROCESSING-события: " + processingInbox);
        }
        return warnings;
    }

    private List<String> supportedBrokerTypes() {
        LinkedHashSet<String> supported = new LinkedHashSet<>();
        messageBusAdapters.forEach(adapter -> supported.addAll(adapter.supportedBrokerTypes()));
        return supported.stream().sorted().toList();
    }
}
