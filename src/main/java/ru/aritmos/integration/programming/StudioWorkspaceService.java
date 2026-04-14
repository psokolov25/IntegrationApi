package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.eventing.EventInboxService;
import ru.aritmos.integration.eventing.EventOutboxService;
import ru.aritmos.integration.service.BranchStateCache;

import java.time.Instant;
import java.util.Comparator;
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
        runtime.put("httpProcessing", Map.of(
                "enabled", configuration.getProgrammableApi().getHttpProcessing().isEnabled(),
                "addDirectionHeader", configuration.getProgrammableApi().getHttpProcessing().isAddDirectionHeader(),
                "directionHeaderName", configuration.getProgrammableApi().getHttpProcessing().getDirectionHeaderName(),
                "requestEnvelopeEnabled", configuration.getProgrammableApi().getHttpProcessing().isRequestEnvelopeEnabled(),
                "responseBodyMaxChars", configuration.getProgrammableApi().getHttpProcessing().getResponseBodyMaxChars(),
                "parseJsonBody", configuration.getProgrammableApi().getHttpProcessing().isParseJsonBody()
        ));

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
        settings.put("httpProcessingEnabled", configuration.getProgrammableApi().getHttpProcessing().isEnabled());
        settings.put("httpProcessingDirectionHeader", configuration.getProgrammableApi().getHttpProcessing().getDirectionHeaderName());

        Map<String, Object> gui = new LinkedHashMap<>();
        gui.put("tabs", List.of("scripts", "debug", "connectors", "inbox-outbox", "settings", "templates"));
        gui.put("ideReady", true);
        gui.put("warnings", buildWarnings(unsupportedBrokerTypes, outboxService.deadSize(), inboxService.processingSize()));
        gui.put("actions", List.of(
                Map.of("id", "studio.bootstrap", "method", "GET", "path", "/api/v2/program/studio/bootstrap"),
                Map.of("id", "studio.settings.get", "method", "GET", "path", "/api/v2/program/studio/settings"),
                Map.of("id", "studio.settings.put", "method", "PUT", "path", "/api/v2/program/studio/settings"),
                Map.of("id", "studio.settings.export", "method", "GET", "path", "/api/v2/program/studio/settings/export"),
                Map.of("id", "studio.settings.import", "method", "POST", "path", "/api/v2/program/studio/settings/import"),
                Map.of("id", "studio.capabilities", "method", "GET", "path", "/api/v2/program/studio/capabilities"),
                Map.of("id", "studio.operations", "method", "POST", "path", "/api/v2/program/studio/operations"),
                Map.of("id", "studio.http.processing.profile.export", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"EXPORT_HTTP_PROCESSING_PROFILE\"}"),
                Map.of("id", "studio.http.processing.profile.preview", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"IMPORT_HTTP_PROCESSING_PROFILE_PREVIEW\",\"parameters\":{\"httpProcessingProfile\":{\"enabled\":true,\"addDirectionHeader\":true,\"directionHeaderName\":\"X-Integration-Direction\",\"requestEnvelopeEnabled\":true,\"parseJsonBody\":true,\"responseBodyMaxChars\":2000}}}"),
                Map.of("id", "studio.http.processing.profile.apply", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"IMPORT_HTTP_PROCESSING_PROFILE_APPLY\",\"parameters\":{\"httpProcessingProfile\":{\"enabled\":true,\"addDirectionHeader\":true,\"directionHeaderName\":\"X-Integration-Direction\",\"requestEnvelopeEnabled\":true,\"parseJsonBody\":true,\"responseBodyMaxChars\":2000}}}"),
                Map.of("id", "studio.dashboard.snapshot", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"DASHBOARD_SNAPSHOT\",\"parameters\":{\"debugHistoryLimit\":20}}"),
                Map.of("id", "studio.http.processing.preview", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"PREVIEW_HTTP_PROCESSING\"}"),
                Map.of("id", "studio.http.processing.matrix", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"PREVIEW_HTTP_PROCESSING_MATRIX\"}"),
                Map.of("id", "studio.connector.profile.preview", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"PREVIEW_CONNECTOR_PROFILE\",\"parameters\":{\"brokerType\":\"KAFKA\"}}"),
                Map.of("id", "studio.connector.profile.validate", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"VALIDATE_CONNECTOR_CONFIG\",\"parameters\":{\"brokerType\":\"WEBHOOK_HTTP\",\"properties\":{\"url\":\"https://gateway.local/events\"}}}"),
                Map.of("id", "studio.openapi.clients.generate", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"GENERATE_OPENAPI_REST_CLIENTS\",\"parameters\":{\"openApiUrl\":\"https://visitmanager.local/openapi.yml\",\"serviceId\":\"visit-manager\"}}"),
                Map.of("id", "studio.openapi.clients.apply", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"APPLY_OPENAPI_REST_CLIENTS_TOOLKIT\",\"parameters\":{\"replaceExisting\":false,\"generated\":{\"serviceId\":\"visit-manager\",\"externalRestServicePreset\":{\"id\":\"visit-manager\",\"baseUrl\":\"https://visitmanager.local\"},\"scripts\":[]}}}"),
                Map.of("id", "studio.connector.presets.export", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"EXPORT_CONNECTOR_PRESETS\"}"),
                Map.of("id", "studio.connector.presets.import.preview", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"IMPORT_CONNECTOR_PRESETS_PREVIEW\",\"parameters\":{\"messageBrokers\":[{\"id\":\"webhook-bus\",\"type\":\"WEBHOOK_HTTP\",\"properties\":{\"url\":\"https://gateway.local/events\"}}]}}"),
                Map.of("id", "studio.connector.presets.import.diff", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"IMPORT_CONNECTOR_PRESETS_DIFF\",\"parameters\":{\"messageBrokers\":[{\"id\":\"webhook-bus\",\"type\":\"WEBHOOK_HTTP\",\"properties\":{\"url\":\"https://gateway.local/events\"}}]}}"),
                Map.of("id", "studio.connector.presets.import.apply", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"IMPORT_CONNECTOR_PRESETS_APPLY\",\"parameters\":{\"replaceExisting\":false,\"messageBrokers\":[{\"id\":\"webhook-bus\",\"type\":\"WEBHOOK_HTTP\",\"properties\":{\"url\":\"https://gateway.local/events\"}}]}}"),
                Map.of("id", "studio.integration.bundle.export", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"EXPORT_INTEGRATION_CONNECTOR_BUNDLE\"}"),
                Map.of("id", "studio.integration.bundle.preview", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"IMPORT_INTEGRATION_CONNECTOR_BUNDLE_PREVIEW\",\"parameters\":{\"bundle\":{\"httpProcessingProfile\":{\"enabled\":true,\"addDirectionHeader\":true,\"directionHeaderName\":\"X-Integration-Direction\",\"requestEnvelopeEnabled\":true,\"parseJsonBody\":true,\"responseBodyMaxChars\":2000},\"connectorPresets\":{\"messageBrokers\":[{\"id\":\"webhook-bus\",\"type\":\"WEBHOOK_HTTP\",\"properties\":{\"url\":\"https://gateway.local/events\"}}],\"externalRestServices\":[{\"id\":\"crm\",\"baseUrl\":\"https://crm.local\"}]}}}}"),
                Map.of("id", "studio.integration.bundle.apply", "method", "POST", "path", "/api/v2/program/studio/operations {\"operation\":\"IMPORT_INTEGRATION_CONNECTOR_BUNDLE_APPLY\",\"parameters\":{\"replaceExisting\":false,\"bundle\":{\"httpProcessingProfile\":{\"enabled\":true,\"addDirectionHeader\":true,\"directionHeaderName\":\"X-Integration-Direction\",\"requestEnvelopeEnabled\":true,\"parseJsonBody\":true,\"responseBodyMaxChars\":2000},\"connectorPresets\":{\"messageBrokers\":[{\"id\":\"webhook-bus\",\"type\":\"WEBHOOK_HTTP\",\"properties\":{\"url\":\"https://gateway.local/events\"}}],\"externalRestServices\":[{\"id\":\"crm\",\"baseUrl\":\"https://crm.local\"}]}}}}"),
                Map.of("id", "connectors.crm.identify-client", "method", "POST", "path", "/api/v2/program/connectors/crm/identify-client"),
                Map.of("id", "connectors.crm.medical-services", "method", "POST", "path", "/api/v2/program/connectors/crm/medical-services"),
                Map.of("id", "connectors.crm.prebooking", "method", "POST", "path", "/api/v2/program/connectors/crm/prebooking"),
                Map.of("id", "templates.import.preview", "method", "POST", "path", "/api/v2/program/templates/import/preview"),
                Map.of("id", "templates.import", "method", "POST", "path", "/api/v2/program/templates/import"),
                Map.of("id", "templates.export", "method", "POST", "path", "/api/v2/program/templates/export"),
                Map.of("id", "connectors.catalog", "method", "GET", "path", "/api/v2/program/connectors/catalog"),
                Map.of("id", "connectors.broker-types", "method", "GET", "path", "/api/v2/program/connectors/broker-types"),
                Map.of("id", "connectors.health", "method", "GET", "path", "/api/v2/program/connectors/health"),
                Map.of("id", "events.outbox.flush", "method", "POST", "path", "/api/v2/events/outbox/flush?limit=100"),
                Map.of("id", "events.inbox.recover", "method", "POST", "path", "/api/v2/events/inbox/recover-stale"),
                Map.of("id", "events.snapshot.export", "method", "GET", "path", "/api/v2/events/export"),
                Map.of("id", "events.snapshot.import.preview", "method", "POST", "path", "/api/v2/events/import/preview"),
                Map.of("id", "events.snapshot.import", "method", "POST", "path", "/api/v2/events/import")
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
        List<BranchStateDto> snapshot = branchStateCache.snapshot();
        List<Map<String, Object>> recent = snapshot.stream()
                .sorted(Comparator
                        .comparing(BranchStateDto::updatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(BranchStateDto::branchId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(BranchStateDto::sourceVisitManagerId, Comparator.nullsLast(Comparator.naturalOrder())))
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

        Map<String, Long> byVisitManager = snapshot.stream()
                .collect(Collectors.groupingBy(item -> item.sourceVisitManagerId() == null ? "" : item.sourceVisitManagerId(),
                        LinkedHashMap::new, Collectors.counting()));

        return Map.of(
                "enabled", true,
                "total", snapshot.size(),
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
                "supportedBrokerTypes", supportedBrokerTypes(),
                "supportedBrokerProfiles", supportedBrokerProfiles(),
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
        snapshot.put("httpProcessing", Map.of(
                "enabled", configuration.getProgrammableApi().getHttpProcessing().isEnabled(),
                "addDirectionHeader", configuration.getProgrammableApi().getHttpProcessing().isAddDirectionHeader(),
                "directionHeaderName", configuration.getProgrammableApi().getHttpProcessing().getDirectionHeaderName(),
                "requestEnvelopeEnabled", configuration.getProgrammableApi().getHttpProcessing().isRequestEnvelopeEnabled(),
                "responseBodyMaxChars", configuration.getProgrammableApi().getHttpProcessing().getResponseBodyMaxChars(),
                "parseJsonBody", configuration.getProgrammableApi().getHttpProcessing().isParseJsonBody()
        ));
        snapshot.put("generatedAt", Instant.now().toString());
        return snapshot;
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

    private List<Map<String, Object>> supportedBrokerProfiles() {
        return messageBusAdapters.stream()
                .flatMap(adapter -> adapter.supportedBrokerProfiles().stream())
                .filter(item -> item.containsKey("type"))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                item -> String.valueOf(item.get("type")).trim().toUpperCase(),
                                item -> item,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ),
                        map -> List.copyOf(map.values())
                ));
    }
}
