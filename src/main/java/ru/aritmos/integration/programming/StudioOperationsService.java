package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.StudioOperationCatalogItemDto;
import ru.aritmos.integration.eventing.EventDispatcherService;

import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Оркестратор служебных операций programmable-студии для GUI/IDE.
 */
@Singleton
public class StudioOperationsService {

    private final EventDispatcherService eventDispatcherService;
    private final ScriptDebugHistoryService scriptDebugHistoryService;
    private final StudioWorkspaceService studioWorkspaceService;
    private final StudioEditorSettingsService studioEditorSettingsService;
    private final ProgrammableHttpExchangeProcessor httpExchangeProcessor;
    private final IntegrationGatewayConfiguration configuration;
    private final RuntimeMutableConfigurationService runtimeMutableConfigurationService;
    private final List<CustomerMessageBusAdapter> messageBusAdapters;
    private final GroovyScriptStorage scriptStorage;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Inject
    public StudioOperationsService(EventDispatcherService eventDispatcherService,
                                   ScriptDebugHistoryService scriptDebugHistoryService,
                                   StudioWorkspaceService studioWorkspaceService,
                                   StudioEditorSettingsService studioEditorSettingsService,
                                   ProgrammableHttpExchangeProcessor httpExchangeProcessor,
                                   IntegrationGatewayConfiguration configuration,
                                   RuntimeMutableConfigurationService runtimeMutableConfigurationService,
                                   List<CustomerMessageBusAdapter> messageBusAdapters,
                                   GroovyScriptStorage scriptStorage) {
        this.eventDispatcherService = eventDispatcherService;
        this.scriptDebugHistoryService = scriptDebugHistoryService;
        this.studioWorkspaceService = studioWorkspaceService;
        this.studioEditorSettingsService = studioEditorSettingsService;
        this.httpExchangeProcessor = httpExchangeProcessor;
        this.configuration = configuration;
        this.runtimeMutableConfigurationService = runtimeMutableConfigurationService;
        this.messageBusAdapters = messageBusAdapters;
        this.scriptStorage = scriptStorage;
    }

    StudioOperationsService(EventDispatcherService eventDispatcherService,
                            ScriptDebugHistoryService scriptDebugHistoryService,
                            StudioWorkspaceService studioWorkspaceService,
                            StudioEditorSettingsService studioEditorSettingsService,
                            ProgrammableHttpExchangeProcessor httpExchangeProcessor,
                            IntegrationGatewayConfiguration configuration,
                            List<CustomerMessageBusAdapter> messageBusAdapters) {
        this(eventDispatcherService, scriptDebugHistoryService, studioWorkspaceService, studioEditorSettingsService,
                httpExchangeProcessor, configuration,
                new RuntimeMutableConfigurationService(configuration, new ru.aritmos.integration.eventing.InMemoryEventingInboxOutboxStorage()),
                messageBusAdapters, new InMemoryGroovyScriptStorage());
    }

    StudioOperationsService(EventDispatcherService eventDispatcherService,
                            ScriptDebugHistoryService scriptDebugHistoryService,
                            StudioWorkspaceService studioWorkspaceService,
                            StudioEditorSettingsService studioEditorSettingsService,
                            ProgrammableHttpExchangeProcessor httpExchangeProcessor,
                            IntegrationGatewayConfiguration configuration,
                            List<CustomerMessageBusAdapter> messageBusAdapters,
                            GroovyScriptStorage scriptStorage) {
        this(eventDispatcherService, scriptDebugHistoryService, studioWorkspaceService, studioEditorSettingsService,
                httpExchangeProcessor, configuration,
                new RuntimeMutableConfigurationService(configuration, new ru.aritmos.integration.eventing.InMemoryEventingInboxOutboxStorage()),
                messageBusAdapters, scriptStorage);
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
            case EXPORT_DEBUG_HISTORY -> {
                String scriptId = stringParam(args.get("scriptId"), "");
                int limit = intParam(args.get("limit"), 50);
                boolean redactSensitive = booleanParam(args.get("redactSensitive"), true);
                List<Map<String, Object>> entries = scriptDebugHistoryService.list(scriptId, limit).stream()
                        .map(item -> toDebugHistoryMap(item, redactSensitive))
                        .toList();
                yield Map.of(
                        "operation", operation.name(),
                        "scriptId", scriptId,
                        "limit", limit,
                        "redactSensitive", redactSensitive,
                        "entries", entries,
                        "total", entries.size(),
                        "exportedAt", Instant.now().toString()
                );
            }
            case IMPORT_DEBUG_HISTORY_PREVIEW -> {
                List<Map<String, Object>> rawEntries = mapListParam(args.get("entries"));
                Map<String, Object> preview = previewDebugHistoryImport(rawEntries);
                yield new LinkedHashMap<>(preview);
            }
            case IMPORT_DEBUG_HISTORY_APPLY -> {
                List<Map<String, Object>> rawEntries = mapListParam(args.get("entries"));
                boolean replaceExisting = booleanParam(args.get("replaceExisting"), false);
                Map<String, Object> preview = previewDebugHistoryImport(rawEntries);
                if (!Boolean.TRUE.equals(preview.get("valid"))) {
                    yield Map.of(
                            "operation", operation.name(),
                            "applied", false,
                            "replaceExisting", replaceExisting,
                            "preview", preview
                    );
                }
                List<ScriptDebugHistoryService.DebugEntry> parsed = parseDebugHistoryEntries(rawEntries);
                if (replaceExisting) {
                    parsed.stream()
                            .map(ScriptDebugHistoryService.DebugEntry::scriptId)
                            .filter(value -> value != null && !value.isBlank())
                            .distinct()
                            .forEach(scriptDebugHistoryService::clear);
                }
                parsed.forEach(scriptDebugHistoryService::record);
                yield Map.of(
                        "operation", operation.name(),
                        "applied", true,
                        "replaceExisting", replaceExisting,
                        "imported", parsed.size(),
                        "preview", preview
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
                    "snapshot", runtimeMutableConfigurationService.snapshot()
            );
            case APPLY_RUNTIME_SETTINGS -> {
                Map<String, Object> runtimeSettings = objectMapParam(args.get("runtimeSettings"));
                yield Map.of(
                        "operation", operation.name(),
                        "applied", true,
                        "snapshot", runtimeMutableConfigurationService.apply(runtimeSettings)
                );
            }
            case RESET_RUNTIME_SETTINGS -> Map.of(
                    "operation", operation.name(),
                    "applied", true,
                    "snapshot", runtimeMutableConfigurationService.resetToDefaults()
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
            case EXPORT_HTTP_PROCESSING_PROFILE -> Map.of(
                    "operation", operation.name(),
                    "httpProcessingProfile", exportHttpProcessingProfile()
            );
            case IMPORT_HTTP_PROCESSING_PROFILE_PREVIEW -> {
                Map<String, Object> profile = objectMapParam(args.get("httpProcessingProfile"));
                String directionHeaderName = stringParam(profile.get("directionHeaderName"), "");
                boolean addDirectionHeader = booleanParam(profile.get("addDirectionHeader"), true);
                int responseBodyMaxChars = intParam(profile.get("responseBodyMaxChars"), 2000);
                List<String> errors = new java.util.ArrayList<>();
                if (addDirectionHeader && directionHeaderName.isBlank()) {
                    errors.add("directionHeaderName обязателен, когда addDirectionHeader=true");
                }
                if (responseBodyMaxChars <= 0) {
                    errors.add("responseBodyMaxChars должен быть > 0");
                }
                yield Map.of(
                        "operation", operation.name(),
                        "valid", errors.isEmpty(),
                        "errors", List.copyOf(errors),
                        "current", exportHttpProcessingProfile(),
                        "candidate", profile
                );
            }
            case IMPORT_HTTP_PROCESSING_PROFILE_APPLY -> {
                Map<String, Object> preview = execute("IMPORT_HTTP_PROCESSING_PROFILE_PREVIEW", args, subjectId);
                if (!Boolean.TRUE.equals(preview.get("valid"))) {
                    yield Map.of(
                            "operation", operation.name(),
                            "applied", false,
                            "preview", preview
                    );
                }
                Map<String, Object> profile = objectMapParam(args.get("httpProcessingProfile"));
                IntegrationGatewayConfiguration.HttpProcessingSettings target = configuration.getProgrammableApi().getHttpProcessing();
                target.setEnabled(booleanParam(profile.get("enabled"), target.isEnabled()));
                target.setAddDirectionHeader(booleanParam(profile.get("addDirectionHeader"), target.isAddDirectionHeader()));
                target.setDirectionHeaderName(stringParam(profile.get("directionHeaderName"), target.getDirectionHeaderName()));
                target.setRequestEnvelopeEnabled(booleanParam(profile.get("requestEnvelopeEnabled"), target.isRequestEnvelopeEnabled()));
                target.setParseJsonBody(booleanParam(profile.get("parseJsonBody"), target.isParseJsonBody()));
                target.setResponseBodyMaxChars(intParam(profile.get("responseBodyMaxChars"), target.getResponseBodyMaxChars()));
                runtimeMutableConfigurationService.persistCurrent();
                yield Map.of(
                        "operation", operation.name(),
                        "applied", true,
                        "httpProcessingProfile", exportHttpProcessingProfile()
                );
            }
            case DASHBOARD_SNAPSHOT -> {
                int limit = intParam(args.get("debugHistoryLimit"), 20);
                yield Map.of(
                        "operation", operation.name(),
                        "debugHistoryLimit", limit,
                        "snapshot", studioWorkspaceService.buildDashboardSnapshot(limit)
                );
            }
            case PREVIEW_HTTP_PROCESSING -> {
                String direction = httpExchangeProcessor.normalizeDirection(stringParam(args.get("direction"),
                        ProgrammableHttpExchangeProcessor.DIRECTION_OUTBOUND_EXTERNAL));
                yield Map.of(
                        "operation", operation.name(),
                        "preview", buildHttpProcessingPreview(direction, args),
                        "supportedDirections", httpExchangeProcessor.supportedDirections()
                );
            }
            case PREVIEW_HTTP_PROCESSING_MATRIX -> Map.of(
                    "operation", operation.name(),
                    "supportedDirections", httpExchangeProcessor.supportedDirections(),
                    "directionPreviews", httpExchangeProcessor.supportedDirections().stream()
                            .map(direction -> Map.of(
                                    "direction", direction,
                                    "preview", buildHttpProcessingPreview(direction, args)
                            ))
                            .toList()
            );
            case PREVIEW_CONNECTOR_PROFILE -> {
                String brokerType = stringParam(args.get("brokerType"), "KAFKA").toUpperCase();
                Map<String, Object> profile = findBrokerProfile(brokerType);
                boolean adapterAvailable = messageBusAdapters.stream().anyMatch(adapter -> adapter.supports(brokerType));
                long configuredCount = configuration.getProgrammableApi().getMessageBrokers().stream()
                        .filter(item -> brokerType.equalsIgnoreCase(item.getType()))
                        .count();
                yield Map.of(
                        "operation", operation.name(),
                        "brokerType", brokerType,
                        "adapterAvailable", adapterAvailable,
                        "configuredBrokersWithType", configuredCount,
                        "profile", profile
                );
            }
            case PROBE_EXTERNAL_REST_SERVICE -> {
                String serviceId = stringParam(args.get("serviceId"), "");
                if (serviceId.isBlank()) {
                    throw new IllegalArgumentException("serviceId обязателен");
                }
                String path = normalizeProbePath(stringParam(args.get("path"), "/health"));
                String method = normalizeProbeMethod(stringParam(args.get("method"), "GET"));
                int timeoutMillis = Math.max(100, intParam(args.get("timeoutMillis"), 3000));
                Map<String, String> headers = stringMapParam(args.get("headers"));
                yield Map.of(
                        "operation", operation.name(),
                        "probe", probeExternalRestService(serviceId, method, path, timeoutMillis, headers)
                );
            }
            case VALIDATE_CONNECTOR_CONFIG -> {
                String brokerType = stringParam(args.get("brokerType"), "KAFKA").toUpperCase();
                Map<String, String> properties = stringMapParam(args.get("properties"));
                Map<String, Object> profile = findBrokerProfile(brokerType);
                List<String> requiredProperties = stringList(profile.get("requiredProperties"));
                if (requiredProperties.isEmpty()) {
                    requiredProperties = profileTemplateKeys(profile);
                }
                List<String> missingRequired = requiredProperties.stream()
                        .filter(key -> !properties.containsKey(key) || properties.get(key).isBlank())
                        .toList();
                List<String> templateKeys = profileTemplateKeys(profile);
                List<String> unknownKeys = properties.keySet().stream()
                        .filter(key -> !templateKeys.isEmpty())
                        .filter(key -> !templateKeys.contains(key))
                        .sorted()
                        .toList();
                List<String> adapterValidationErrors = adapterPropertyViolations(brokerType, properties);
                yield Map.of(
                        "operation", operation.name(),
                        "brokerType", brokerType,
                        "valid", missingRequired.isEmpty() && adapterValidationErrors.isEmpty(),
                        "missingRequiredProperties", missingRequired,
                        "adapterValidationErrors", adapterValidationErrors,
                        "unknownProperties", unknownKeys,
                        "propertyTemplateKeys", templateKeys,
                        "profile", profile
                );
            }
            case GENERATE_OPENAPI_REST_CLIENTS -> {
                String openApiUrl = stringParam(args.get("openApiUrl"), "");
                String serviceIdHint = stringParam(args.get("serviceId"), "");
                Map<String, Object> generated = OpenApiGroovyClientGenerator.generate(openApiUrl, serviceIdHint);
                yield Map.of(
                        "operation", operation.name(),
                        "generated", generated
                );
            }
            case APPLY_OPENAPI_REST_CLIENTS_TOOLKIT -> {
                Map<String, Object> generated = objectMapParam(args.get("generated"));
                boolean replaceExisting = booleanParam(args.get("replaceExisting"), false);
                boolean dryRun = booleanParam(args.get("dryRun"), false);
                Map<String, Object> preview = previewGeneratedOpenApiToolkitApply(generated, replaceExisting);
                Map<String, Object> scriptsReport = applyGeneratedOpenApiScripts(generated, replaceExisting, subjectId, dryRun);
                int appliedServices = applyGeneratedOpenApiExternalService(generated, replaceExisting, dryRun);
                yield Map.of(
                        "operation", operation.name(),
                        "replaceExisting", replaceExisting,
                        "dryRun", dryRun,
                        "preview", preview,
                        "appliedExternalRestServices", appliedServices,
                        "savedScripts", scriptsReport.get("savedScripts"),
                        "skippedExistingScripts", scriptsReport.get("skippedExistingScripts"),
                        "invalidScripts", scriptsReport.get("invalidScripts"),
                        "serviceId", stringParam(generated.get("serviceId"), "")
                );
            }
            case PREVIEW_OPENAPI_REST_CLIENTS_TOOLKIT_APPLY -> {
                Map<String, Object> generated = objectMapParam(args.get("generated"));
                boolean replaceExisting = booleanParam(args.get("replaceExisting"), false);
                Map<String, Object> preview = previewGeneratedOpenApiToolkitApply(generated, replaceExisting);
                yield Map.of(
                        "operation", operation.name(),
                        "replaceExisting", replaceExisting,
                        "preview", preview
                );
            }
            case VALIDATE_GROOVY_SCRIPT_BODY -> {
                String scriptBody = stringParam(args.get("scriptBody"), "");
                GroovyScriptType type = parseScriptType(stringParam(args.get("type"), "VISIT_MANAGER_ACTION"));
                yield validateGroovyScriptBody(type, scriptBody);
            }
            case EXPORT_CONNECTOR_PRESETS -> {
                Map<String, Object> export = exportConnectorPresets();
                yield Map.of(
                        "operation", operation.name(),
                        "metadata", export.get("metadata"),
                        "connectorPresets", export.get("connectorPresets")
                );
            }
            case IMPORT_CONNECTOR_PRESETS_PREVIEW -> {
                List<Map<String, Object>> brokerCandidates = mapListParam(args.get("messageBrokers"));
                List<Map<String, Object>> restCandidates = mapListParam(args.get("externalRestServices"));
                Set<String> duplicateBrokerIds = duplicateIds(brokerCandidates, "id");
                Set<String> duplicateRestIds = duplicateIds(restCandidates, "id");
                Set<String> existingBrokerIds = configuration.getProgrammableApi().getMessageBrokers().stream()
                        .map(IntegrationGatewayConfiguration.MessageBrokerSettings::getId)
                        .map(value -> value == null ? "" : value.trim())
                        .filter(value -> !value.isBlank())
                        .collect(Collectors.toSet());
                Set<String> existingRestIds = configuration.getProgrammableApi().getExternalRestServices().stream()
                        .map(IntegrationGatewayConfiguration.ExternalRestServiceSettings::getId)
                        .map(value -> value == null ? "" : value.trim())
                        .filter(value -> !value.isBlank())
                        .collect(Collectors.toSet());
                List<Map<String, Object>> brokerPreview = brokerCandidates.stream()
                        .map(candidate -> {
                            String brokerType = stringParam(candidate.get("type"), "").toUpperCase();
                            String brokerId = stringParam(candidate.get("id"), "");
                            Map<String, String> properties = stringMapParam(candidate.get("properties"));
                            Map<String, Object> profile = findBrokerProfile(brokerType);
                            List<String> requiredProperties = stringList(profile.get("requiredProperties"));
                            if (requiredProperties.isEmpty()) {
                                requiredProperties = profileTemplateKeys(profile);
                            }
                            List<String> missing = requiredProperties.stream()
                                    .filter(key -> !properties.containsKey(key) || properties.get(key).isBlank())
                                    .toList();
                            List<String> adapterValidationErrors = adapterPropertyViolations(brokerType, properties);
                            boolean duplicateInImport = duplicateBrokerIds.contains(brokerId);
                            boolean conflictsWithExisting = existingBrokerIds.contains(brokerId);
                            boolean profileFound = !"Профиль не найден, используйте connectors/catalog для полного списка"
                                    .equals(profile.get("description"));
                            return Map.of(
                                    "id", brokerId,
                                    "type", brokerType,
                                    "valid", !brokerId.isBlank() && !brokerType.isBlank()
                                            && missing.isEmpty()
                                            && adapterValidationErrors.isEmpty()
                                            && !duplicateInImport,
                                    "missingRequiredProperties", missing,
                                    "adapterValidationErrors", adapterValidationErrors,
                                    "profileFound", profileFound,
                                    "duplicateInImport", duplicateInImport,
                                    "conflictsWithExisting", conflictsWithExisting
                            );
                        })
                        .toList();
                List<Map<String, Object>> restPreview = restCandidates.stream()
                        .map(candidate -> {
                            String serviceId = stringParam(candidate.get("id"), "");
                            String baseUrl = stringParam(candidate.get("baseUrl"), "");
                            boolean duplicateInImport = duplicateRestIds.contains(serviceId);
                            boolean conflictsWithExisting = existingRestIds.contains(serviceId);
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("id", serviceId);
                            item.put("baseUrl", baseUrl);
                            item.put("valid", !serviceId.isBlank() && baseUrl.startsWith("http") && !duplicateInImport);
                            item.put("duplicateInImport", duplicateInImport);
                            item.put("conflictsWithExisting", conflictsWithExisting);
                            return item;
                        })
                        .toList();
                long invalidBrokers = brokerPreview.stream()
                        .filter(item -> !Boolean.TRUE.equals(item.get("valid")))
                        .count();
                long invalidRest = restPreview.stream()
                        .filter(item -> !Boolean.TRUE.equals(item.get("valid")))
                        .count();
                long brokerConflicts = brokerPreview.stream()
                        .filter(item -> Boolean.TRUE.equals(item.get("conflictsWithExisting")))
                        .count();
                long restConflicts = restPreview.stream()
                        .filter(item -> Boolean.TRUE.equals(item.get("conflictsWithExisting")))
                        .count();
                yield Map.of(
                        "operation", operation.name(),
                        "summary", Map.of(
                                "brokersTotal", brokerPreview.size(),
                                "brokersInvalid", invalidBrokers,
                                "brokersConflictsWithExisting", brokerConflicts,
                                "brokersDuplicatesInImport", duplicateBrokerIds.size(),
                                "restServicesTotal", restPreview.size(),
                                "restServicesInvalid", invalidRest,
                                "restServicesConflictsWithExisting", restConflicts,
                                "restServicesDuplicatesInImport", duplicateRestIds.size(),
                                "importable", invalidBrokers == 0 && invalidRest == 0
                        ),
                        "messageBrokersPreview", brokerPreview,
                        "externalRestServicesPreview", restPreview
                );
            }
            case IMPORT_CONNECTOR_PRESETS_APPLY -> {
                boolean replaceExisting = booleanParam(args.get("replaceExisting"), false);
                boolean includeRollbackSnapshot = booleanParam(args.get("includeRollbackSnapshot"), true);
                Map<String, Object> previewResult = execute("IMPORT_CONNECTOR_PRESETS_PREVIEW", args, subjectId);
                Map<String, Object> summary = objectMapParam(previewResult.get("summary"));
                boolean importable = Boolean.TRUE.equals(summary.get("importable"));
                if (!importable) {
                    yield Map.of(
                            "operation", operation.name(),
                            "applied", false,
                            "replaceExisting", replaceExisting,
                            "reason", "Импорт отклонен: есть невалидные элементы. Используйте IMPORT_CONNECTOR_PRESETS_PREVIEW.",
                            "preview", previewResult
                    );
                }
                Map<String, Object> rollbackSnapshot = Map.of(
                        "messageBrokers", configuration.getProgrammableApi().getMessageBrokers(),
                        "externalRestServices", configuration.getProgrammableApi().getExternalRestServices()
                );
                List<Map<String, Object>> brokerCandidates = mapListParam(args.get("messageBrokers"));
                List<Map<String, Object>> restCandidates = mapListParam(args.get("externalRestServices"));
                int appliedBrokers = applyMessageBrokers(brokerCandidates, replaceExisting);
                int appliedRestServices = applyExternalRestServices(restCandidates, replaceExisting);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("operation", operation.name());
                result.put("applied", true);
                result.put("replaceExisting", replaceExisting);
                result.put("appliedMessageBrokers", appliedBrokers);
                result.put("appliedExternalRestServices", appliedRestServices);
                result.put("totalsAfterApply", Map.of(
                        "messageBrokers", configuration.getProgrammableApi().getMessageBrokers().size(),
                        "externalRestServices", configuration.getProgrammableApi().getExternalRestServices().size()
                ));
                if (includeRollbackSnapshot) {
                    result.put("rollbackSnapshot", rollbackSnapshot);
                }
                yield Map.copyOf(result);
            }
            case IMPORT_CONNECTOR_PRESETS_DIFF -> {
                List<Map<String, Object>> brokerCandidates = mapListParam(args.get("messageBrokers"));
                List<Map<String, Object>> restCandidates = mapListParam(args.get("externalRestServices"));
                Map<String, IntegrationGatewayConfiguration.MessageBrokerSettings> existingBrokers = configuration
                        .getProgrammableApi().getMessageBrokers().stream()
                        .filter(item -> item.getId() != null && !item.getId().isBlank())
                        .collect(Collectors.toMap(IntegrationGatewayConfiguration.MessageBrokerSettings::getId, Function.identity(),
                                (left, right) -> left));
                Map<String, IntegrationGatewayConfiguration.ExternalRestServiceSettings> existingRest = configuration
                        .getProgrammableApi().getExternalRestServices().stream()
                        .filter(item -> item.getId() != null && !item.getId().isBlank())
                        .collect(Collectors.toMap(IntegrationGatewayConfiguration.ExternalRestServiceSettings::getId, Function.identity(),
                                (left, right) -> left));
                List<Map<String, String>> brokerDiff = brokerCandidates.stream()
                        .map(candidate -> {
                            String id = stringParam(candidate.get("id"), "");
                            String type = stringParam(candidate.get("type"), "").toUpperCase();
                            Map<String, String> properties = stringMapParam(candidate.get("properties"));
                            IntegrationGatewayConfiguration.MessageBrokerSettings existing = existingBrokers.get(id);
                            String changeType;
                            if (existing == null) {
                                changeType = "CREATE";
                            } else if (!type.equalsIgnoreCase(existing.getType())
                                    || !properties.equals(existing.getProperties())) {
                                changeType = "UPDATE";
                            } else {
                                changeType = "NO_CHANGES";
                            }
                            return Map.of(
                                    "id", id,
                                    "changeType", changeType,
                                    "type", type
                            );
                        })
                        .toList();
                List<Map<String, String>> restDiff = restCandidates.stream()
                        .map(candidate -> {
                            String id = stringParam(candidate.get("id"), "");
                            String baseUrl = stringParam(candidate.get("baseUrl"), "");
                            IntegrationGatewayConfiguration.ExternalRestServiceSettings existing = existingRest.get(id);
                            String changeType;
                            if (existing == null) {
                                changeType = "CREATE";
                            } else if (!baseUrl.equals(existing.getBaseUrl())
                                    || !stringMapParam(candidate.get("defaultHeaders")).equals(existing.getDefaultHeaders())) {
                                changeType = "UPDATE";
                            } else {
                                changeType = "NO_CHANGES";
                            }
                            return Map.of(
                                    "id", id,
                                    "changeType", changeType,
                                    "baseUrl", baseUrl
                            );
                        })
                        .toList();
                long brokerCreates = brokerDiff.stream().filter(item -> "CREATE".equals(item.get("changeType"))).count();
                long brokerUpdates = brokerDiff.stream().filter(item -> "UPDATE".equals(item.get("changeType"))).count();
                long restCreates = restDiff.stream().filter(item -> "CREATE".equals(item.get("changeType"))).count();
                long restUpdates = restDiff.stream().filter(item -> "UPDATE".equals(item.get("changeType"))).count();
                yield Map.of(
                        "operation", operation.name(),
                        "summary", Map.of(
                                "messageBrokersCreate", brokerCreates,
                                "messageBrokersUpdate", brokerUpdates,
                                "externalRestServicesCreate", restCreates,
                                "externalRestServicesUpdate", restUpdates
                        )
                        ,
                        "messageBrokersDiff", brokerDiff,
                        "externalRestServicesDiff", restDiff
                );
            }
            case EXPORT_INTEGRATION_CONNECTOR_BUNDLE -> {
                Map<String, Object> connectorExport = exportConnectorPresets();
                yield Map.of(
                        "operation", operation.name(),
                        "bundle", Map.of(
                                "metadata", Map.of(
                                        "formatVersion", 1,
                                        "exportedAt", Instant.now().toString(),
                                        "source", "studio.integration-connector-bundle"
                                ),
                                "httpProcessingProfile", exportHttpProcessingProfile(),
                                "connectorPresets", connectorExport.get("connectorPresets")
                        )
                );
            }
            case IMPORT_INTEGRATION_CONNECTOR_BUNDLE_PREVIEW -> {
                Map<String, Object> bundle = objectMapParam(args.get("bundle"));
                Map<String, Object> httpProfile = objectMapParam(bundle.get("httpProcessingProfile"));
                Map<String, Object> connectorPresets = objectMapParam(bundle.get("connectorPresets"));
                Map<String, Object> connectorPreview = execute("IMPORT_CONNECTOR_PRESETS_PREVIEW", Map.of(
                        "messageBrokers", mapListParam(connectorPresets.get("messageBrokers")),
                        "externalRestServices", mapListParam(connectorPresets.get("externalRestServices"))
                ), subjectId);
                Map<String, Object> httpPreview = execute("IMPORT_HTTP_PROCESSING_PROFILE_PREVIEW", Map.of(
                        "httpProcessingProfile", httpProfile
                ), subjectId);
                boolean connectorImportable = Boolean.TRUE.equals(objectMapParam(connectorPreview.get("summary")).get("importable"));
                boolean httpImportable = Boolean.TRUE.equals(httpPreview.get("valid"));
                yield Map.of(
                        "operation", operation.name(),
                        "importable", connectorImportable && httpImportable,
                        "httpProcessingPreview", httpPreview,
                        "connectorPresetsPreview", connectorPreview
                );
            }
            case IMPORT_INTEGRATION_CONNECTOR_BUNDLE_APPLY -> {
                boolean replaceExisting = booleanParam(args.get("replaceExisting"), false);
                Map<String, Object> preview = execute("IMPORT_INTEGRATION_CONNECTOR_BUNDLE_PREVIEW", args, subjectId);
                if (!Boolean.TRUE.equals(preview.get("importable"))) {
                    yield Map.of(
                            "operation", operation.name(),
                            "applied", false,
                            "replaceExisting", replaceExisting,
                            "preview", preview
                    );
                }
                Map<String, Object> bundle = objectMapParam(args.get("bundle"));
                Map<String, Object> httpProfile = objectMapParam(bundle.get("httpProcessingProfile"));
                Map<String, Object> connectorPresets = objectMapParam(bundle.get("connectorPresets"));
                Map<String, Object> httpApply = execute("IMPORT_HTTP_PROCESSING_PROFILE_APPLY", Map.of(
                        "httpProcessingProfile", httpProfile
                ), subjectId);
                Map<String, Object> connectorApply = execute("IMPORT_CONNECTOR_PRESETS_APPLY", Map.of(
                        "replaceExisting", replaceExisting,
                        "includeRollbackSnapshot", booleanParam(args.get("includeRollbackSnapshot"), true),
                        "messageBrokers", mapListParam(connectorPresets.get("messageBrokers")),
                        "externalRestServices", mapListParam(connectorPresets.get("externalRestServices"))
                ), subjectId);
                yield Map.of(
                        "operation", operation.name(),
                        "applied", Boolean.TRUE.equals(httpApply.get("applied")) && Boolean.TRUE.equals(connectorApply.get("applied")),
                        "replaceExisting", replaceExisting,
                        "httpProcessingApply", httpApply,
                        "connectorPresetsApply", connectorApply
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

    private long longParam(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> stringMapParam(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, val) -> {
            String normalizedKey = key == null ? "" : String.valueOf(key).trim();
            if (!normalizedKey.isBlank()) {
                result.put(normalizedKey, val == null ? "" : String.valueOf(val));
            }
        });
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMapParam(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, val) -> {
            String normalizedKey = key == null ? "" : String.valueOf(key).trim();
            if (!normalizedKey.isBlank()) {
                result.put(normalizedKey, val);
            }
        });
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> listMapParam(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        map.forEach((key, val) -> {
            String normalizedKey = key == null ? "" : String.valueOf(key).trim();
            if (normalizedKey.isBlank()) {
                return;
            }
            if (val instanceof List<?> list) {
                result.put(normalizedKey, list.stream().map(String::valueOf).toList());
            } else if (val != null) {
                result.put(normalizedKey, List.of(String.valueOf(val)));
            }
        });
        return Map.copyOf(result);
    }

    private String stringParam(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private Map<String, Object> buildHttpProcessingPreview(String direction, Map<String, Object> args) {
        int status = intParam(args.get("responseStatus"), 200);
        String responseBody = stringParam(args.get("responseBody"), "{\"ok\":true}");
        Map<String, String> headers = stringMapParam(args.get("headers"));
        Map<String, Object> body = objectMapParam(args.get("body"));
        Map<String, List<String>> responseHeaders = listMapParam(args.get("responseHeaders"));
        return Map.of(
                "direction", direction,
                "requestHeaders", httpExchangeProcessor.enrichHeaders(headers, direction),
                "requestBody", httpExchangeProcessor.enrichBody(body, direction),
                "response", httpExchangeProcessor.processRawResponse(status, responseBody, responseHeaders, direction)
        );
    }

    private Map<String, Object> findBrokerProfile(String brokerType) {
        return messageBusAdapters.stream()
                .flatMap(adapter -> adapter.supportedBrokerProfiles().stream())
                .filter(item -> brokerType.equalsIgnoreCase(String.valueOf(item.getOrDefault("type", ""))))
                .findFirst()
                .map(Map::copyOf)
                .orElse(Map.of(
                        "type", brokerType,
                        "description", "Профиль не найден, используйте connectors/catalog для полного списка"
                ));
    }

    private List<String> adapterPropertyViolations(String brokerType, Map<String, String> properties) {
        return messageBusAdapters.stream()
                .filter(adapter -> adapter.supports(brokerType))
                .flatMap(adapter -> adapter.validateProperties(properties).stream())
                .distinct()
                .sorted()
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> profileTemplateKeys(Map<String, Object> profile) {
        Object rawTemplate = profile.get("propertyTemplate");
        if (!(rawTemplate instanceof Map<?, ?> template)) {
            return List.of();
        }
        return template.keySet().stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .sorted()
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapListParam(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private Set<String> duplicateIds(List<Map<String, Object>> items, String fieldName) {
        return items.stream()
                .map(item -> stringParam(item.get(fieldName), ""))
                .filter(value -> !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private int applyMessageBrokers(List<Map<String, Object>> candidates, boolean replaceExisting) {
        Map<String, IntegrationGatewayConfiguration.MessageBrokerSettings> existingById = configuration
                .getProgrammableApi()
                .getMessageBrokers()
                .stream()
                .filter(item -> item.getId() != null && !item.getId().isBlank())
                .collect(Collectors.toMap(IntegrationGatewayConfiguration.MessageBrokerSettings::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        int applied = 0;
        for (Map<String, Object> candidate : candidates) {
            String id = stringParam(candidate.get("id"), "");
            if (id.isBlank()) {
                continue;
            }
            if (!replaceExisting && existingById.containsKey(id)) {
                continue;
            }
            IntegrationGatewayConfiguration.MessageBrokerSettings item = new IntegrationGatewayConfiguration.MessageBrokerSettings();
            item.setId(id);
            item.setType(stringParam(candidate.get("type"), "LOGGING").toUpperCase());
            item.setEnabled(booleanParam(candidate.get("enabled"), true));
            item.setProperties(stringMapParam(candidate.get("properties")));
            existingById.put(id, item);
            applied++;
        }
        configuration.getProgrammableApi().setMessageBrokers(List.copyOf(existingById.values()));
        return applied;
    }

    private int applyExternalRestServices(List<Map<String, Object>> candidates, boolean replaceExisting) {
        Map<String, IntegrationGatewayConfiguration.ExternalRestServiceSettings> existingById = configuration
                .getProgrammableApi()
                .getExternalRestServices()
                .stream()
                .filter(item -> item.getId() != null && !item.getId().isBlank())
                .collect(Collectors.toMap(IntegrationGatewayConfiguration.ExternalRestServiceSettings::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        int applied = 0;
        for (Map<String, Object> candidate : candidates) {
            String id = stringParam(candidate.get("id"), "");
            if (id.isBlank()) {
                continue;
            }
            if (!replaceExisting && existingById.containsKey(id)) {
                continue;
            }
            IntegrationGatewayConfiguration.ExternalRestServiceSettings item =
                    new IntegrationGatewayConfiguration.ExternalRestServiceSettings();
            item.setId(id);
            item.setBaseUrl(stringParam(candidate.get("baseUrl"), ""));
            item.setDefaultHeaders(stringMapParam(candidate.get("defaultHeaders")));
            existingById.put(id, item);
            applied++;
        }
        configuration.getProgrammableApi().setExternalRestServices(List.copyOf(existingById.values()));
        return applied;
    }

    private int applyGeneratedOpenApiExternalService(Map<String, Object> generated, boolean replaceExisting, boolean dryRun) {
        Map<String, Object> externalPreset = objectMapParam(generated.get("externalRestServicePreset"));
        if (externalPreset.isEmpty()) {
            return 0;
        }
        if (dryRun) {
            String id = stringParam(externalPreset.get("id"), "");
            if (id.isBlank()) {
                return 0;
            }
            boolean exists = configuration.getProgrammableApi().getExternalRestServices().stream()
                    .map(IntegrationGatewayConfiguration.ExternalRestServiceSettings::getId)
                    .anyMatch(id::equals);
            return (!replaceExisting && exists) ? 0 : 1;
        }
        return applyExternalRestServices(List.of(externalPreset), replaceExisting);
    }

    private Map<String, Object> previewGeneratedOpenApiToolkitApply(Map<String, Object> generated, boolean replaceExisting) {
        Map<String, Object> externalPreset = objectMapParam(generated.get("externalRestServicePreset"));
        String externalServiceId = stringParam(externalPreset.get("id"), "");
        boolean externalServiceExists = !externalServiceId.isBlank() && configuration.getProgrammableApi().getExternalRestServices().stream()
                .map(IntegrationGatewayConfiguration.ExternalRestServiceSettings::getId)
                .anyMatch(externalServiceId::equals);
        boolean willApplyExternalService = !externalPreset.isEmpty()
                && !externalServiceId.isBlank()
                && (replaceExisting || !externalServiceExists);
        String externalServiceDecision = externalPreset.isEmpty()
                ? "SKIP_EMPTY_PRESET"
                : externalServiceId.isBlank()
                ? "SKIP_MISSING_ID"
                : (!replaceExisting && externalServiceExists)
                ? "SKIP_EXISTS"
                : "APPLY";

        List<Map<String, Object>> scripts = mapListParam(generated.get("scripts"));
        List<Map<String, Object>> scriptDecisions = new java.util.ArrayList<>();
        int willSaveScripts = 0;
        int skippedExistingScripts = 0;
        int invalidScripts = 0;
        for (Map<String, Object> script : scripts) {
            Map<String, Object> saveRequest = objectMapParam(script.get("saveScriptRequest"));
            String scriptId = stringParam(saveRequest.get("scriptId"), stringParam(script.get("scriptId"), ""));
            String scriptBody = stringParam(saveRequest.get("scriptBody"), stringParam(script.get("scriptBody"), ""));
            GroovyScriptType type = parseScriptType(stringParam(saveRequest.get("type"), "VISIT_MANAGER_ACTION"));
            boolean exists = !scriptId.isBlank() && scriptStorage.get(scriptId) != null;
            boolean missingId = scriptId.isBlank();
            boolean missingBody = scriptBody.isBlank();
            Map<String, Object> validation = missingId || missingBody
                    ? Map.of("ok", false, "message", missingId ? "scriptId обязателен" : "scriptBody обязателен")
                    : validateGroovyScriptBody(type, scriptBody);
            boolean valid = Boolean.TRUE.equals(validation.get("ok"));
            boolean skippedExisting = exists && !replaceExisting;
            boolean willSave = !missingId && !missingBody && valid && !skippedExisting;
            String decision = missingId
                    ? "SKIP_MISSING_ID"
                    : missingBody
                    ? "SKIP_MISSING_BODY"
                    : skippedExisting
                    ? "SKIP_EXISTS"
                    : valid
                    ? "APPLY"
                    : "SKIP_INVALID";
            if (willSave) {
                willSaveScripts++;
            } else if (skippedExisting) {
                skippedExistingScripts++;
            } else if (!valid && !missingId && !missingBody) {
                invalidScripts++;
            }
            scriptDecisions.add(Map.of(
                    "scriptId", scriptId,
                    "type", type.name(),
                    "exists", exists,
                    "valid", valid,
                    "decision", decision,
                    "validationMessage", stringParam(validation.get("message"), "")
            ));
        }

        return Map.of(
                "externalRestService", Map.of(
                        "id", externalServiceId,
                        "exists", externalServiceExists,
                        "decision", externalServiceDecision,
                        "willApply", willApplyExternalService
                ),
                "scripts", List.copyOf(scriptDecisions),
                "summary", Map.of(
                        "scriptsTotal", scripts.size(),
                        "scriptsWillSave", willSaveScripts,
                        "scriptsSkippedExisting", skippedExistingScripts,
                        "scriptsInvalid", invalidScripts,
                        "externalRestServiceWillApply", willApplyExternalService
                )
        );
    }

    private Map<String, Object> applyGeneratedOpenApiScripts(Map<String, Object> generated,
                                                             boolean replaceExisting,
                                                             String subjectId,
                                                             boolean dryRun) {
        List<Map<String, Object>> scripts = mapListParam(generated.get("scripts"));
        int applied = 0;
        int skippedExisting = 0;
        List<Map<String, Object>> invalidScripts = new java.util.ArrayList<>();
        for (Map<String, Object> script : scripts) {
            Map<String, Object> saveRequest = objectMapParam(script.get("saveScriptRequest"));
            String scriptId = stringParam(saveRequest.get("scriptId"), stringParam(script.get("scriptId"), ""));
            if (scriptId.isBlank()) {
                continue;
            }
            if (!replaceExisting && scriptStorage.get(scriptId) != null) {
                skippedExisting++;
                continue;
            }
            String scriptBody = stringParam(saveRequest.get("scriptBody"), stringParam(script.get("scriptBody"), ""));
            if (scriptBody.isBlank()) {
                continue;
            }
            String description = stringParam(saveRequest.get("description"), "OpenAPI generated script");
            GroovyScriptType type = parseScriptType(stringParam(saveRequest.get("type"), "VISIT_MANAGER_ACTION"));
            Map<String, Object> validation = validateGroovyScriptBody(type, scriptBody);
            if (!Boolean.TRUE.equals(validation.get("ok"))) {
                invalidScripts.add(Map.of(
                        "scriptId", scriptId,
                        "type", type.name(),
                        "message", stringParam(validation.get("message"), "Синтаксическая ошибка Groovy")
                ));
                continue;
            }
            if (!dryRun) {
                scriptStorage.save(new StoredGroovyScript(
                        scriptId,
                        type,
                        scriptBody,
                        description,
                        Instant.now(),
                        subjectId
                ));
            }
            applied++;
        }
        return Map.of(
                "savedScripts", applied,
                "skippedExistingScripts", skippedExisting,
                "invalidScripts", List.copyOf(invalidScripts)
        );
    }

    private GroovyScriptType parseScriptType(String rawType) {
        try {
            return GroovyScriptType.valueOf(rawType == null ? "VISIT_MANAGER_ACTION" : rawType.trim().toUpperCase());
        } catch (Exception ex) {
            return GroovyScriptType.VISIT_MANAGER_ACTION;
        }
    }

    private Map<String, Object> validateGroovyScriptBody(GroovyScriptType type, String scriptBody) {
        if (scriptBody == null || scriptBody.isBlank()) {
            return Map.of(
                    "operation", Operation.VALIDATE_GROOVY_SCRIPT_BODY.name(),
                    "ok", false,
                    "type", type.name(),
                    "message", "scriptBody обязателен",
                    "bindingHints", bindingHints(type)
            );
        }
        Binding binding = new Binding();
        binding.setVariable("payload", Map.of());
        binding.setVariable("input", Map.of());
        binding.setVariable("params", Map.of());
        binding.setVariable("parameters", Map.of());
        binding.setVariable("context", Map.of());
        binding.setVariable("execution", Map.of("payload", Map.of(), "parameters", Map.of(), "context", Map.of()));
        binding.setVariable("subject", "studio-preview");
        binding.setVariable("externalRestClient", null);
        binding.setVariable("messageBusGateway", null);
        binding.setVariable("visitManagerInvoker", null);
        try {
            new GroovyShell(binding).parse(scriptBody);
            return Map.of(
                    "operation", Operation.VALIDATE_GROOVY_SCRIPT_BODY.name(),
                    "ok", true,
                    "type", type.name(),
                    "message", "Скрипт успешно прошел синтаксическую валидацию",
                    "bindingHints", bindingHints(type)
            );
        } catch (Exception ex) {
            return Map.of(
                    "operation", Operation.VALIDATE_GROOVY_SCRIPT_BODY.name(),
                    "ok", false,
                    "type", type.name(),
                    "message", stringParam(ex.getMessage(), "Синтаксическая ошибка Groovy"),
                    "bindingHints", bindingHints(type)
            );
        }
    }

    private List<String> bindingHints(GroovyScriptType type) {
        List<String> common = new java.util.ArrayList<>(List.of(
                "payload", "input", "params", "parameters", "context", "execution", "subject",
                "externalRestClient", "messageBusGateway"
        ));
        if (type == GroovyScriptType.VISIT_MANAGER_ACTION || type == GroovyScriptType.MESSAGE_BUS_REACTION) {
            common.add("visitManagerInvoker");
        }
        if (type == GroovyScriptType.BRANCH_CACHE_QUERY) {
            common.add("branchStateSnapshot");
            common.add("getBranchState");
        }
        return List.copyOf(common);
    }

    private Map<String, Object> toDebugHistoryMap(ScriptDebugHistoryService.DebugEntry item, boolean redactSensitive) {
        return Map.of(
                "scriptId", stringParam(item.scriptId(), ""),
                "startedAt", item.startedAt() == null ? "" : item.startedAt().toString(),
                "durationMs", item.durationMs(),
                "ok", item.ok(),
                "result", item.result() == null ? Map.of() : redactValue(item.result(), "", redactSensitive),
                "error", stringParam(item.error(), ""),
                "payload", item.payload() == null ? Map.of() : redactValue(item.payload(), "payload", redactSensitive),
                "parameters", item.parameters() == null ? Map.of() : redactValue(item.parameters(), "parameters", redactSensitive),
                "context", item.context() == null ? Map.of() : redactValue(item.context(), "context", redactSensitive)
        );
    }

    @SuppressWarnings("unchecked")
    private Object redactValue(Object value, String keyPath, boolean enabled) {
        if (!enabled || value == null) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            map.forEach((key, val) -> {
                String keyString = key == null ? "" : String.valueOf(key);
                String path = keyPath.isBlank() ? keyString : keyPath + "." + keyString;
                if (isSensitiveKey(keyString)) {
                    redacted.put(keyString, "***");
                } else {
                    redacted.put(keyString, redactValue(val, path, true));
                }
            });
            return Map.copyOf(redacted);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> redactValue(item, keyPath, true))
                    .toList();
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.replace("-", "").replace("_", "").toLowerCase();
        return normalized.contains("password")
                || normalized.contains("passwd")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("apikey")
                || normalized.contains("authorization");
    }

    private Map<String, Object> previewDebugHistoryImport(List<Map<String, Object>> rawEntries) {
        List<String> errors = new java.util.ArrayList<>();
        int index = 0;
        for (Map<String, Object> raw : rawEntries) {
            String scriptId = stringParam(raw.get("scriptId"), "");
            if (scriptId.isBlank()) {
                errors.add("entries[" + index + "].scriptId обязателен");
            }
            if (parseInstantOrNull(raw.get("startedAt")) == null) {
                errors.add("entries[" + index + "].startedAt должен быть ISO-8601");
            }
            long durationMs = longParam(raw.get("durationMs"), -1);
            if (durationMs < 0) {
                errors.add("entries[" + index + "].durationMs должен быть >= 0");
            }
            index++;
        }
        return Map.of(
                "operation", Operation.IMPORT_DEBUG_HISTORY_PREVIEW.name(),
                "valid", errors.isEmpty(),
                "errors", List.copyOf(errors),
                "entriesTotal", rawEntries.size(),
                "scripts", rawEntries.stream()
                        .map(item -> stringParam(item.get("scriptId"), ""))
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .sorted()
                        .toList()
        );
    }

    private List<ScriptDebugHistoryService.DebugEntry> parseDebugHistoryEntries(List<Map<String, Object>> rawEntries) {
        List<ScriptDebugHistoryService.DebugEntry> parsed = new java.util.ArrayList<>();
        for (Map<String, Object> raw : rawEntries) {
            parsed.add(new ScriptDebugHistoryService.DebugEntry(
                    stringParam(raw.get("scriptId"), ""),
                    parseInstantOrNull(raw.get("startedAt")),
                    Math.max(0, longParam(raw.get("durationMs"), 0)),
                    booleanParam(raw.get("ok"), false),
                    raw.get("result"),
                    stringParam(raw.get("error"), ""),
                    objectMapParam(raw.get("payload")),
                    objectMapParam(raw.get("parameters")),
                    objectMapParam(raw.get("context"))
            ));
        }
        return parsed;
    }

    private Instant parseInstantOrNull(Object value) {
        String text = stringParam(value, "");
        if (text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> exportConnectorPresets() {
        List<IntegrationGatewayConfiguration.ExternalRestServiceSettings> externalRestServices =
                configuration.getProgrammableApi().getExternalRestServices();
        List<IntegrationGatewayConfiguration.MessageBrokerSettings> messageBrokers =
                configuration.getProgrammableApi().getMessageBrokers();
        List<Map<String, Object>> supportedProfiles = messageBusAdapters.stream()
                .flatMap(adapter -> adapter.supportedBrokerProfiles().stream())
                .toList();
        return Map.of(
                "metadata", Map.of(
                        "formatVersion", 2,
                        "exportedAt", Instant.now().toString(),
                        "source", "studio.connector-presets",
                        "totalExternalRestServices", externalRestServices.size(),
                        "totalMessageBrokers", messageBrokers.size(),
                        "totalSupportedProfiles", supportedProfiles.size()
                ),
                "connectorPresets", Map.of(
                        "externalRestServices", externalRestServices,
                        "messageBrokers", messageBrokers,
                        "supportedBrokerProfiles", supportedProfiles
                )
        );
    }

    private Map<String, Object> exportHttpProcessingProfile() {
        IntegrationGatewayConfiguration.HttpProcessingSettings settings = configuration.getProgrammableApi().getHttpProcessing();
        return Map.of(
                "enabled", settings.isEnabled(),
                "addDirectionHeader", settings.isAddDirectionHeader(),
                "directionHeaderName", settings.getDirectionHeaderName(),
                "requestEnvelopeEnabled", settings.isRequestEnvelopeEnabled(),
                "parseJsonBody", settings.isParseJsonBody(),
                "responseBodyMaxChars", settings.getResponseBodyMaxChars()
        );
    }

    private String normalizeProbePath(String path) {
        if (path == null || path.isBlank()) {
            return "/health";
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private String normalizeProbeMethod(String method) {
        String normalized = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
        return switch (normalized) {
            case "GET", "HEAD", "OPTIONS" -> normalized;
            default -> "GET";
        };
    }

    private Map<String, Object> probeExternalRestService(String serviceId,
                                                         String method,
                                                         String path,
                                                         int timeoutMillis,
                                                         Map<String, String> headers) {
        IntegrationGatewayConfiguration.ExternalRestServiceSettings service = configuration.getProgrammableApi()
                .getExternalRestServices().stream()
                .filter(item -> serviceId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Внешний REST service не найден: " + serviceId));
        String baseUrl = stringParam(service.getBaseUrl(), "");
        if (baseUrl.isBlank()) {
            return Map.of(
                    "serviceId", serviceId,
                    "method", method,
                    "path", path,
                    "timeoutMillis", timeoutMillis,
                    "ok", false,
                    "reachable", false,
                    "status", "DOWN",
                    "error", "Не задан baseUrl"
            );
        }
        String url = baseUrl + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis));
        Map<String, String> allHeaders = new LinkedHashMap<>();
        if (service.getDefaultHeaders() != null) {
            allHeaders.putAll(service.getDefaultHeaders());
        }
        allHeaders.putAll(headers);
        allHeaders.forEach(builder::header);
        HttpRequest request = switch (method) {
            case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            case "OPTIONS" -> builder.method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
            default -> builder.GET().build();
        };
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            boolean ok = statusCode >= 200 && statusCode < 300;
            boolean reachable = statusCode > 0 && statusCode < 500;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("serviceId", serviceId);
            result.put("method", method);
            result.put("path", path);
            result.put("url", url);
            result.put("timeoutMillis", timeoutMillis);
            result.put("statusCode", statusCode);
            result.put("ok", ok);
            result.put("reachable", reachable);
            result.put("status", ok ? "UP" : (reachable ? "DEGRADED" : "DOWN"));
            result.put("responseBodyPreview", trimPreview(response.body(), 500));
            result.put("checkedAt", Instant.now().toString());
            return Map.copyOf(result);
        } catch (Exception ex) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("serviceId", serviceId);
            result.put("method", method);
            result.put("path", path);
            result.put("url", url);
            result.put("timeoutMillis", timeoutMillis);
            result.put("ok", false);
            result.put("reachable", false);
            result.put("status", "DOWN");
            result.put("error", ex.getClass().getSimpleName() + ": " + stringParam(ex.getMessage(), "probe failure"));
            result.put("checkedAt", Instant.now().toString());
            return Map.copyOf(result);
        }
    }

    private String trimPreview(String value, int limit) {
        if (value == null) {
            return "";
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    enum Operation {
        FLUSH_OUTBOX("Повторно отправить pending/failed outbox-сообщения", Map.of("limit", 100)),
        RECOVER_STALE_INBOX("Перевести stale PROCESSING inbox-записи в FAILED", Map.of()),
        CLEAR_DEBUG_HISTORY("Очистить debug history (весь или по scriptId)", Map.of("scriptId", "")),
        EXPORT_DEBUG_HISTORY("Экспортировать debug history Groovy-скриптов для GUI backup", Map.of("scriptId", "", "limit", 50, "redactSensitive", true)),
        IMPORT_DEBUG_HISTORY_PREVIEW("Предпросмотр импортируемого debug history без применения", Map.of(
                "entries", List.of(Map.of(
                        "scriptId", "script-1",
                        "startedAt", "2026-01-01T10:00:00Z",
                        "durationMs", 42,
                        "ok", true,
                        "result", Map.of("ok", true),
                        "error", "",
                        "payload", Map.of(),
                        "parameters", Map.of(),
                        "context", Map.of()
                ))
        )),
        IMPORT_DEBUG_HISTORY_APPLY("Импортировать debug history Groovy-скриптов в runtime", Map.of(
                "replaceExisting", false,
                "entries", List.of(Map.of(
                        "scriptId", "script-1",
                        "startedAt", "2026-01-01T10:00:00Z",
                        "durationMs", 42,
                        "ok", true,
                        "result", Map.of("ok", true),
                        "error", "",
                        "payload", Map.of(),
                        "parameters", Map.of(),
                        "context", Map.of()
                ))
        )),
        REFRESH_BOOTSTRAP("Получить свежий studio bootstrap snapshot", Map.of("debugHistoryLimit", 20)),
        SNAPSHOT_INBOX_OUTBOX("Получить диагностический срез inbox/outbox для IDE", Map.of("limit", 20, "status", "", "includeSent", false)),
        SNAPSHOT_VISIT_MANAGERS("Получить диагностический срез конфигурации VisitManager", Map.of()),
        SNAPSHOT_BRANCH_CACHE("Получить диагностический срез кэша отделений branch-state", Map.of("limit", 50)),
        SNAPSHOT_EXTERNAL_SERVICES("Получить диагностический срез внешних сервисов и брокеров", Map.of()),
        SNAPSHOT_RUNTIME_SETTINGS("Получить runtime-срез настроек контрольной панели", Map.of()),
        APPLY_RUNTIME_SETTINGS("Применить и сохранить изменяемые runtime-настройки службы", Map.of(
                "runtimeSettings", Map.of(
                        "aggregateMaxBranches", 200,
                        "aggregateRequestTimeoutMillis", 3000,
                        "outboxBackoffSeconds", 5,
                        "outboxMaxAttempts", 20,
                        "inboxProcessingTimeoutSeconds", 120,
                        "outboxAutoFlushBatchSize", 100,
                        "maxPayloadFields", 100,
                        "httpProcessing", Map.of(
                                "enabled", true,
                                "addDirectionHeader", true,
                                "directionHeaderName", "X-Integration-Direction",
                                "requestEnvelopeEnabled", false,
                                "responseBodyMaxChars", 4000,
                                "parseJsonBody", true
                        )
                )
        )),
        RESET_RUNTIME_SETTINGS("Сбросить runtime-настройки к значениям старта процесса и сохранить в storage", Map.of()),
        EXPORT_EDITOR_SETTINGS("Экспортировать настройки IDE-редактора для GUI backup", Map.of()),
        PREVIEW_EVENTING_MAINTENANCE("Предпросмотр maintenance inbox/outbox/DLQ/processed без изменений", Map.of()),
        EXPORT_EVENTING_SNAPSHOT("Экспортировать snapshot eventing для backup/import", Map.of()),
        EXPORT_HTTP_PROCESSING_PROFILE("Экспортировать профиль programmable HTTP processing для backup", Map.of()),
        IMPORT_HTTP_PROCESSING_PROFILE_PREVIEW("Предпросмотр импортируемого профиля programmable HTTP processing", Map.of(
                "httpProcessingProfile", Map.of(
                        "enabled", true,
                        "addDirectionHeader", true,
                        "directionHeaderName", "X-Integration-Direction",
                        "requestEnvelopeEnabled", true,
                        "parseJsonBody", true,
                        "responseBodyMaxChars", 2000
                )
        )),
        IMPORT_HTTP_PROCESSING_PROFILE_APPLY("Применить импортируемый профиль programmable HTTP processing", Map.of(
                "httpProcessingProfile", Map.of(
                        "enabled", true,
                        "addDirectionHeader", true,
                        "directionHeaderName", "X-Integration-Direction",
                        "requestEnvelopeEnabled", true,
                        "parseJsonBody", true,
                        "responseBodyMaxChars", 2000
                )
        )),
        DASHBOARD_SNAPSHOT("Получить сводный dashboard snapshot для GUI", Map.of("debugHistoryLimit", 20)),
        PREVIEW_HTTP_PROCESSING("Предпросмотр кастомной обработки programmable HTTP request/response для GUI", Map.of(
                "direction", "OUTBOUND_EXTERNAL",
                "headers", Map.of("X-Trace", "demo"),
                "body", Map.of("demo", true),
                "responseStatus", 200,
                "responseBody", "{\"ok\":true}",
                "responseHeaders", Map.of("Content-Type", List.of("application/json"))
        )),
        PREVIEW_HTTP_PROCESSING_MATRIX("Сравнить обработку programmable HTTP сразу по всем направлениям", Map.of(
                "headers", Map.of("X-Trace", "demo"),
                "body", Map.of("demo", true),
                "responseStatus", 200,
                "responseBody", "{\"ok\":true}",
                "responseHeaders", Map.of("Content-Type", List.of("application/json"))
        )),
        PREVIEW_CONNECTOR_PROFILE("Предпросмотр профиля внешнего broker/шины для GUI формы настройки", Map.of(
                "brokerType", "KAFKA"
        )),
        PROBE_EXTERNAL_REST_SERVICE("Проверить доступность внешнего REST-сервиса для GUI/Groovy runtime", Map.of(
                "serviceId", "crm",
                "path", "/health",
                "method", "GET",
                "timeoutMillis", 3000,
                "headers", Map.of()
        )),
        VALIDATE_GROOVY_SCRIPT_BODY("Проверить синтаксис Groovy-скрипта и binding hints для IDE", Map.of(
                "type", "VISIT_MANAGER_ACTION",
                "scriptBody", "return [ok: true]"
        )),
        VALIDATE_CONNECTOR_CONFIG("Проверить набор свойств коннектора по профилю brokerType", Map.of(
                "brokerType", "WEBHOOK_HTTP",
                "properties", Map.of("url", "https://gateway.customer.local/integration/events", "method", "POST")
        )),
        GENERATE_OPENAPI_REST_CLIENTS("Сгенерировать Groovy REST-клиенты из OpenAPI URL/файла для IDE", Map.of(
                "openApiUrl", "https://visitmanager.local/openapi.yml",
                "serviceId", "visit-manager"
        )),
        PREVIEW_OPENAPI_REST_CLIENTS_TOOLKIT_APPLY("Предпросмотреть применение generated toolkit OpenAPI-клиентов без изменения runtime", Map.of(
                "replaceExisting", false,
                "generated", Map.of(
                        "serviceId", "visit-manager",
                        "externalRestServicePreset", Map.of("id", "visit-manager", "baseUrl", "https://visitmanager.local"),
                        "scripts", List.of(Map.of(
                                "scriptId", "openapi-visit-manager-get-queues",
                                "saveScriptRequest", Map.of(
                                        "scriptId", "openapi-visit-manager-get-queues",
                                        "type", "VISIT_MANAGER_ACTION",
                                        "description", "OpenAPI generated script",
                                        "scriptBody", "return [ok: true]"
                                )
                        ))
                )
        )),
        APPLY_OPENAPI_REST_CLIENTS_TOOLKIT("Применить generated toolkit OpenAPI-клиентов (REST service + scripts)", Map.of(
                "replaceExisting", false,
                "dryRun", false,
                "generated", Map.of(
                        "serviceId", "visit-manager",
                        "externalRestServicePreset", Map.of("id", "visit-manager", "baseUrl", "https://visitmanager.local"),
                        "scripts", List.of(Map.of(
                                "scriptId", "openapi-visit-manager-get-queues",
                                "saveScriptRequest", Map.of(
                                        "scriptId", "openapi-visit-manager-get-queues",
                                        "type", "VISIT_MANAGER_ACTION",
                                        "description", "OpenAPI generated script",
                                        "scriptBody", "return externalRestClient.invoke('visit-manager', 'GET', '/api/v1/queues', payload instanceof Map ? payload : [:], params.headers instanceof Map ? params.headers : [:])"
                                )
                        ))
                )
        )),
        EXPORT_CONNECTOR_PRESETS("Экспортировать текущие presets коннекторов (REST/broker/profiles) для GUI backup", Map.of()),
        IMPORT_CONNECTOR_PRESETS_PREVIEW("Предпросмотр импортируемых presets коннекторов без применения", Map.of(
                "messageBrokers", List.of(Map.of("id", "webhook-bus", "type", "WEBHOOK_HTTP",
                        "properties", Map.of("url", "https://gateway.customer.local/events"))),
                "externalRestServices", List.of(Map.of("id", "crm", "baseUrl", "https://crm.customer.local"))
        )),
        IMPORT_CONNECTOR_PRESETS_APPLY("Применить импортируемые presets коннекторов (после preview)", Map.of(
                "replaceExisting", false,
                "includeRollbackSnapshot", true,
                "messageBrokers", List.of(Map.of("id", "webhook-bus", "type", "WEBHOOK_HTTP",
                        "properties", Map.of("url", "https://gateway.customer.local/events"))),
                "externalRestServices", List.of(Map.of("id", "crm", "baseUrl", "https://crm.customer.local"))
        )),
        IMPORT_CONNECTOR_PRESETS_DIFF("Сравнить импортируемые presets с текущими конфигурациями (create/update/no_changes)", Map.of(
                "messageBrokers", List.of(Map.of("id", "webhook-bus", "type", "WEBHOOK_HTTP",
                        "properties", Map.of("url", "https://gateway.customer.local/events"))),
                "externalRestServices", List.of(Map.of("id", "crm", "baseUrl", "https://crm.customer.local"))
        )),
        EXPORT_INTEGRATION_CONNECTOR_BUNDLE("Экспортировать единый bundle интеграции (HTTP processing + connector presets)", Map.of()),
        IMPORT_INTEGRATION_CONNECTOR_BUNDLE_PREVIEW("Предпросмотр импортируемого bundle интеграции без применения", Map.of(
                "bundle", Map.of(
                        "httpProcessingProfile", Map.of(
                                "enabled", true,
                                "addDirectionHeader", true,
                                "directionHeaderName", "X-Integration-Direction",
                                "requestEnvelopeEnabled", true,
                                "parseJsonBody", true,
                                "responseBodyMaxChars", 2000
                        ),
                        "connectorPresets", Map.of(
                                "messageBrokers", List.of(Map.of("id", "webhook-bus", "type", "WEBHOOK_HTTP",
                                        "properties", Map.of("url", "https://gateway.customer.local/events"))),
                                "externalRestServices", List.of(Map.of("id", "crm", "baseUrl", "https://crm.customer.local"))
                        )
                )
        )),
        IMPORT_INTEGRATION_CONNECTOR_BUNDLE_APPLY("Применить bundle интеграции (после preview)", Map.of(
                "replaceExisting", false,
                "includeRollbackSnapshot", true,
                "bundle", Map.of(
                        "httpProcessingProfile", Map.of(
                                "enabled", true,
                                "addDirectionHeader", true,
                                "directionHeaderName", "X-Integration-Direction",
                                "requestEnvelopeEnabled", true,
                                "parseJsonBody", true,
                                "responseBodyMaxChars", 2000
                        ),
                        "connectorPresets", Map.of(
                                "messageBrokers", List.of(Map.of("id", "webhook-bus", "type", "WEBHOOK_HTTP",
                                        "properties", Map.of("url", "https://gateway.customer.local/events"))),
                                "externalRestServices", List.of(Map.of("id", "crm", "baseUrl", "https://crm.customer.local"))
                        )
                )
        ));

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
