package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.EventInboxService;
import ru.aritmos.integration.eventing.EventOutboxService;

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

    public StudioWorkspaceService(IntegrationGatewayConfiguration configuration,
                                  EventInboxService inboxService,
                                  EventOutboxService outboxService,
                                  GroovyScriptStorage scriptStorage,
                                  ScriptDebugHistoryService debugHistoryService,
                                  List<CustomerMessageBusAdapter> messageBusAdapters) {
        this.configuration = configuration;
        this.inboxService = inboxService;
        this.outboxService = outboxService;
        this.scriptStorage = scriptStorage;
        this.debugHistoryService = debugHistoryService;
        this.messageBusAdapters = messageBusAdapters;
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
                Map.of("id", "studio.capabilities", "method", "GET", "path", "/api/v1/program/studio/capabilities"),
                Map.of("id", "studio.operations", "method", "POST", "path", "/api/v1/program/studio/operations"),
                Map.of("id", "connectors.health", "method", "GET", "path", "/api/v1/program/connectors/health"),
                Map.of("id", "events.outbox.flush", "method", "POST", "path", "/api/v1/events/outbox/flush?limit=100"),
                Map.of("id", "events.inbox.recover", "method", "POST", "path", "/api/v1/events/inbox/recover-stale")
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
                        "group", "ide-editor",
                        "title", "Проверить IDE-историю и настройки",
                        "check", "Сверить debugHistoryRecent + editorSettings/editorCapabilities",
                        "api", "GET /api/v1/program/studio/bootstrap?debugHistoryLimit=20"
                ),
                Map.of(
                        "order", 5,
                        "group", "settings",
                        "title", "Проверить и при необходимости обновить настройки редактора",
                        "check", "Проверить theme/fontSize и обновить через PUT",
                        "api", "GET|PUT /api/v1/program/studio/settings"
                ),
                Map.of(
                        "order", 6,
                        "group", "gui-ops",
                        "title", "Выполнить операционные действия",
                        "check", "Запустить FLUSH_OUTBOX / RECOVER_STALE_INBOX / CLEAR_DEBUG_HISTORY",
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
