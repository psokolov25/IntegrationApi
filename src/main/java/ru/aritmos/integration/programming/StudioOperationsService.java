package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.StudioOperationCatalogItemDto;
import ru.aritmos.integration.eventing.EventDispatcherService;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final List<CustomerMessageBusAdapter> messageBusAdapters;

    public StudioOperationsService(EventDispatcherService eventDispatcherService,
                                   ScriptDebugHistoryService scriptDebugHistoryService,
                                   StudioWorkspaceService studioWorkspaceService,
                                   StudioEditorSettingsService studioEditorSettingsService,
                                   ProgrammableHttpExchangeProcessor httpExchangeProcessor,
                                   IntegrationGatewayConfiguration configuration,
                                   List<CustomerMessageBusAdapter> messageBusAdapters) {
        this.eventDispatcherService = eventDispatcherService;
        this.scriptDebugHistoryService = scriptDebugHistoryService;
        this.studioWorkspaceService = studioWorkspaceService;
        this.studioEditorSettingsService = studioEditorSettingsService;
        this.httpExchangeProcessor = httpExchangeProcessor;
        this.configuration = configuration;
        this.messageBusAdapters = messageBusAdapters;
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
                yield Map.of(
                        "operation", operation.name(),
                        "brokerType", brokerType,
                        "valid", missingRequired.isEmpty(),
                        "missingRequiredProperties", missingRequired,
                        "unknownProperties", unknownKeys,
                        "propertyTemplateKeys", templateKeys,
                        "profile", profile
                );
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
                            boolean duplicateInImport = duplicateBrokerIds.contains(brokerId);
                            boolean conflictsWithExisting = existingBrokerIds.contains(brokerId);
                            boolean profileFound = !"Профиль не найден, используйте connectors/catalog для полного списка"
                                    .equals(profile.get("description"));
                            return Map.of(
                                    "id", brokerId,
                                    "type", brokerType,
                                    "valid", !brokerId.isBlank() && !brokerType.isBlank()
                                            && missing.isEmpty()
                                            && !duplicateInImport,
                                    "missingRequiredProperties", missing,
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
        VALIDATE_CONNECTOR_CONFIG("Проверить набор свойств коннектора по профилю brokerType", Map.of(
                "brokerType", "WEBHOOK_HTTP",
                "properties", Map.of("url", "https://gateway.customer.local/integration/events", "method", "POST")
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
