package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integration.domain.CustomerLookupRequest;
import ru.aritmos.integration.domain.CustomerMedicalServicesRequest;
import ru.aritmos.integration.domain.CustomerOptimalServiceSelectionRequest;
import ru.aritmos.integration.domain.CustomerPrebookingRequest;
import ru.aritmos.integration.domain.CustomerServiceAvailabilityRequest;
import ru.aritmos.integration.security.core.SubjectPrincipal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Реализация CRM integration gateway поверх существующего programmable REST connector.
 */
@Singleton
public class ExternalRestCustomerCrmIntegrationGateway implements CustomerCrmIntegrationGateway {

    private static final String IDENTIFY_PATH = "/api/v1/customers/identify";
    private static final String SERVICES_PATH = "/api/v1/medical-services/available";
    private static final String PREBOOKING_PATH = "/api/v1/pre-appointments";
    private static final String PREBOOKING_TIME_WINDOWS_PATH = "/api/v1/pre-appointments/time-windows";
    public static final String DEFAULT_OPTIMAL_SERVICE_SELECTION_SCRIPT = """
            def services = (input.services ?: [])
                    .findAll { it.serviceId }
                    .collect { service ->
                        def waitingCount = (service.waitingCount ?: service.queueLength ?: 0) as BigDecimal
                        def standardWaitMinutes = (service.standardWaitMinutes ?: service.avgWaitMinutes ?: 0) as BigDecimal
                        def predictedWaitMinutes = waitingCount * standardWaitMinutes
                        return service + [predictedWaitMinutes: predictedWaitMinutes]
                    }
                    .sort { a, b ->
                        (a.predictedWaitMinutes as BigDecimal) <=> (b.predictedWaitMinutes as BigDecimal)
                    }
            return [
                    selectedService: services ? services[0] : null,
                    rankedServices : services
            ]
            """;

    private final ExternalRestClient externalRestClient;
    private final CustomerMessageBusGateway customerMessageBusGateway;
    private final GroovyScriptService groovyScriptService;
    private final ObjectMapper objectMapper;

    public ExternalRestCustomerCrmIntegrationGateway(ExternalRestClient externalRestClient,
                                                     CustomerMessageBusGateway customerMessageBusGateway,
                                                     GroovyScriptService groovyScriptService,
                                                     ObjectMapper objectMapper) {
        this.externalRestClient = externalRestClient;
        this.customerMessageBusGateway = customerMessageBusGateway;
        this.groovyScriptService = groovyScriptService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> identifyClient(CustomerLookupRequest request, SubjectPrincipal subject) {
        CustomerConnectorType connectorType = resolveConnectorType(request.connectorType());
        String serviceId = resolveServiceId(request.serviceId(), connectorType);
        String identifierType = normalizeIdentifierType(request.identifierType());
        String identifierValue = requireIdentifierValue(request.identifierValue());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identifier", Map.of(
                "type", identifierType,
                "value", identifierValue
        ));
        payload.put("filters", safeMap(request.filters()));
        Map<String, Object> response = executeConnector(
                connectorType,
                serviceId,
                normalizePath(request.path(), IDENTIFY_PATH),
                request.brokerId(),
                request.topic(),
                request.messageKey(),
                "crm.identify-client",
                payload,
                safeHeaders(request.headers())
        );
        Map<String, Object> result = Map.of(
                "operation", "IDENTIFY_CLIENT_IN_EXTERNAL_CRM",
                "serviceId", serviceId,
                "connectorType", connectorType.name(),
                "identifierType", identifierType,
                "identifierValueMasked", maskIdentifier(identifierValue),
                "result", response
        );
        return applyResponseScript(result, response, request.responseScriptId(), request.responseScriptParameters(), request.responseScriptContext(), subject);
    }

    @Override
    public Map<String, Object> fetchAvailableMedicalServices(CustomerMedicalServicesRequest request, SubjectPrincipal subject) {
        CustomerConnectorType connectorType = resolveConnectorType(request.connectorType());
        String serviceId = resolveServiceId(request.serviceId(), connectorType);
        String identifierType = normalizeIdentifierType(request.identifierType());
        String identifierValue = requireIdentifierValue(request.identifierValue());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identifier", Map.of(
                "type", identifierType,
                "value", identifierValue
        ));
        payload.put("branchIds", sanitizeBranches(request.branchIds()));
        payload.put("filters", safeMap(request.filters()));
        Map<String, Object> response = executeConnector(
                connectorType,
                serviceId,
                normalizePath(request.path(), SERVICES_PATH),
                request.brokerId(),
                request.topic(),
                request.messageKey(),
                "crm.medical-services",
                payload,
                safeHeaders(request.headers())
        );
        Map<String, Object> result = Map.of(
                "operation", "FETCH_AVAILABLE_MEDICAL_SERVICES",
                "serviceId", serviceId,
                "connectorType", connectorType.name(),
                "identifierType", identifierType,
                "identifierValueMasked", maskIdentifier(identifierValue),
                "result", response
        );
        return applyResponseScript(result, response, request.responseScriptId(), request.responseScriptParameters(), request.responseScriptContext(), subject);
    }

    @Override
    public Map<String, Object> fetchMedicalServicesWithPrerequisites(CustomerMedicalServicesRequest request, SubjectPrincipal subject) {
        Map<String, Object> base = fetchAvailableMedicalServices(request, subject);
        Map<String, Object> connectorResponse = safeMapValue(base.get("result"));
        List<Map<String, Object>> services = normalizeServiceCatalog(connectorResponse);
        return Map.of(
                "operation", "FETCH_MEDICAL_SERVICES_WITH_PREREQUISITES",
                "customerIdentifierMasked", maskIdentifier(requireIdentifierValue(request.identifierValue())),
                "connectorType", base.getOrDefault("connectorType", "REST_API"),
                "serviceCount", services.size(),
                "services", services,
                "source", base
        );
    }

    @Override
    public Map<String, Object> calculateEligibleMedicalServices(CustomerServiceAvailabilityRequest request, SubjectPrincipal subject) {
        if (request == null) {
            throw new IllegalArgumentException("request обязателен");
        }
        List<Map<String, Object>> normalizedCatalog = resolveCatalog(request, subject);
        Set<String> completed = sanitizeIdSet(request.completedServiceIds());
        Set<String> candidates = sanitizeIdSet(request.candidateServiceIds());
        List<Map<String, Object>> eligible = new ArrayList<>();

        for (Map<String, Object> service : normalizedCatalog) {
            String serviceId = String.valueOf(service.getOrDefault("serviceId", "")).trim();
            if (serviceId.isBlank()) {
                continue;
            }
            if (!candidates.isEmpty() && !candidates.contains(serviceId)) {
                continue;
            }
            Set<String> prerequisites = sanitizeIdSet(toStringList(service.get("prerequisiteServiceIds")));
            if (completed.containsAll(prerequisites)) {
                Map<String, Object> item = new LinkedHashMap<>(service);
                item.put("availableNow", true);
                item.put("missingPrerequisiteServiceIds", List.of());
                eligible.add(item);
            }
        }

        return Map.of(
                "operation", "CALCULATE_ELIGIBLE_MEDICAL_SERVICES",
                "customerId", request.customerId() == null ? "" : request.customerId().trim(),
                "completedServiceIds", List.copyOf(completed),
                "requestedCandidateServiceIds", List.copyOf(candidates),
                "eligibleServices", eligible,
                "catalogSize", normalizedCatalog.size(),
                "eligibleCount", eligible.size()
        );
    }

    @Override
    public Map<String, Object> fetchPrebookingData(CustomerPrebookingRequest request, SubjectPrincipal subject) {
        return fetchUnifiedPrebookingData(request, subject);
    }

    @Override
    public Map<String, Object> fetchUnifiedPrebookingData(CustomerPrebookingRequest request, SubjectPrincipal subject) {
        CustomerConnectorType connectorType = resolveConnectorType(request.connectorType());
        CustomerPrebookingQueryMode queryMode = resolvePrebookingQueryMode(request.queryMode());
        String serviceId = resolveServiceId(request.serviceId(), connectorType);
        String identifierType = normalizeIdentifierType(request.identifierType());
        String identifierValue = requireIdentifierValue(request.identifierValue());
        if (queryMode == CustomerPrebookingQueryMode.LEGACY_PREBOOKING) {
            return fetchLegacyPrebookingData(request, subject, connectorType, serviceId, identifierType, identifierValue);
        }
        Map<String, Object> slotsResponse = Map.of();
        Map<String, Object> servicesResponse = Map.of();
        List<Map<String, Object>> timeWindows = List.of();
        List<Map<String, Object>> services = List.of();

        if (queryMode.requiresTimeWindows()) {
            Map<String, Object> payload = buildPrebookingPayload(identifierType, identifierValue, request);
            slotsResponse = executeConnector(
                    connectorType,
                    serviceId,
                    normalizePath(request.timeWindowsPath(), normalizePath(request.path(), PREBOOKING_TIME_WINDOWS_PATH)),
                    request.brokerId(),
                    request.topic(),
                    request.messageKey(),
                    "crm.prebooking.time-windows",
                    payload,
                    safeHeaders(request.headers())
            );
            timeWindows = normalizeTimeWindows(slotsResponse);
        }

        if (queryMode.requiresServices()) {
            Map<String, Object> payload = buildPrebookingPayload(identifierType, identifierValue, request);
            servicesResponse = executeConnector(
                    connectorType,
                    serviceId,
                    normalizePath(request.servicesPath(), normalizePath(request.path(), SERVICES_PATH)),
                    request.brokerId(),
                    request.topic(),
                    request.messageKey(),
                    "crm.prebooking.services",
                    payload,
                    safeHeaders(request.headers())
            );
            List<Map<String, Object>> normalizedServices = normalizeServiceCatalog(servicesResponse);
            services = queryMode.requiresPrerequisites()
                    ? normalizedServices
                    : toFlatServices(normalizedServices);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", "FETCH_UNIFIED_PREBOOKING_DATA");
        result.put("serviceId", serviceId);
        result.put("connectorType", connectorType.name());
        result.put("queryMode", queryMode.name());
        result.put("identifierType", identifierType);
        result.put("identifierValueMasked", maskIdentifier(identifierValue));
        result.put("timeWindows", timeWindows);
        result.put("timeWindowCount", timeWindows.size());
        result.put("services", services);
        result.put("serviceCount", services.size());
        result.put("result", Map.of(
                "timeWindowsResponse", slotsResponse,
                "servicesResponse", servicesResponse
        ));
        Map<String, Object> scriptSource = Map.of(
                "timeWindowsResponse", slotsResponse,
                "servicesResponse", servicesResponse,
                "timeWindows", timeWindows,
                "services", services
        );
        return applyResponseScript(result, scriptSource, request.responseScriptId(), request.responseScriptParameters(), request.responseScriptContext(), subject);
    }

    @Override
    public Map<String, Object> selectOptimalMedicalService(CustomerOptimalServiceSelectionRequest request, SubjectPrincipal subject) {
        if (request == null) {
            throw new IllegalArgumentException("request обязателен");
        }
        List<Map<String, Object>> normalized = normalizeCandidateServices(request.services());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("services обязателен и не должен быть пустым");
        }
        List<Map<String, Object>> ranked = rankServicesByPredictedWait(normalized);
        Map<String, Object> defaultResult = Map.of(
                "selectedService", ranked.get(0),
                "rankedServices", ranked
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", "SELECT_OPTIMAL_MEDICAL_SERVICE");
        result.put("customerId", request.customerId() == null ? "" : request.customerId().trim());
        result.put("services", normalized);
        result.put("rankedServices", ranked);
        result.put("selectedService", ranked.get(0));
        result.put("algorithm", "MIN(waitingCount * standardWaitMinutes)");
        result.put("scriptApplied", false);

        if (request.selectionScriptId() == null || request.selectionScriptId().isBlank()) {
            result.put("decisionSource", "BUILT_IN");
            return result;
        }
        if (subject == null) {
            throw new IllegalArgumentException("selectionScriptId требует аутентифицированного субъекта");
        }
        Map<String, Object> scriptPayload = new LinkedHashMap<>();
        scriptPayload.put("customerId", request.customerId() == null ? "" : request.customerId().trim());
        scriptPayload.put("services", normalized);
        scriptPayload.put("defaultSelection", defaultResult);
        Object transformed = groovyScriptService.executeAdvanced(
                request.selectionScriptId().trim(),
                objectMapper.valueToTree(scriptPayload),
                safeMap(request.selectionScriptParameters()),
                safeMap(request.selectionScriptContext()),
                subject
        );
        Map<String, Object> transformedMap = safeMapValue(transformed);
        Map<String, Object> selectedService = safeMapValue(transformedMap.get("selectedService"));
        if (selectedService.isEmpty()) {
            String selectedServiceId = firstNonBlank(transformedMap, "selectedServiceId", "serviceId");
            if (!selectedServiceId.isBlank()) {
                selectedService = ranked.stream()
                        .filter(item -> selectedServiceId.equals(String.valueOf(item.getOrDefault("serviceId", ""))))
                        .findFirst()
                        .orElse(Map.of());
            }
        }
        result.put("scriptApplied", true);
        result.put("decisionSource", "GROOVY_SCRIPT");
        result.put("selectionScriptId", request.selectionScriptId().trim());
        result.put("scriptResult", transformed);
        result.put("selectedService", selectedService.isEmpty() ? ranked.get(0) : selectedService);
        return result;
    }

    private CustomerConnectorType resolveConnectorType(String rawConnectorType) {
        try {
            return CustomerConnectorType.fromRaw(rawConnectorType);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Некорректный connectorType. Поддерживаются: REST_API, DATA_BUS, MESSAGE_BROKER");
        }
    }

    private CustomerPrebookingQueryMode resolvePrebookingQueryMode(String rawMode) {
        try {
            return CustomerPrebookingQueryMode.fromRaw(rawMode);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Некорректный queryMode. Поддерживаются: LEGACY_PREBOOKING, TIME_WINDOWS, SERVICES_FLAT, SERVICES_WITH_PREREQUISITES, TIME_WINDOWS_AND_SERVICES, TIME_WINDOWS_AND_SERVICES_WITH_PREREQUISITES");
        }
    }

    private Map<String, Object> fetchLegacyPrebookingData(CustomerPrebookingRequest request,
                                                          SubjectPrincipal subject,
                                                          CustomerConnectorType connectorType,
                                                          String serviceId,
                                                          String identifierType,
                                                          String identifierValue) {
        Map<String, Object> payload = buildPrebookingPayload(identifierType, identifierValue, request);
        Map<String, Object> response = executeConnector(
                connectorType,
                serviceId,
                normalizePath(request.path(), PREBOOKING_PATH),
                request.brokerId(),
                request.topic(),
                request.messageKey(),
                "crm.prebooking",
                payload,
                safeHeaders(request.headers())
        );
        Map<String, Object> result = Map.of(
                "operation", "FETCH_PREBOOKING_DATA",
                "serviceId", serviceId,
                "connectorType", connectorType.name(),
                "queryMode", CustomerPrebookingQueryMode.LEGACY_PREBOOKING.name(),
                "identifierType", identifierType,
                "identifierValueMasked", maskIdentifier(identifierValue),
                "result", response
        );
        return applyResponseScript(result, response, request.responseScriptId(), request.responseScriptParameters(), request.responseScriptContext(), subject);
    }

    private Map<String, Object> executeConnector(CustomerConnectorType connectorType,
                                                 String serviceId,
                                                 String restPath,
                                                 String brokerId,
                                                 String topic,
                                                 String messageKey,
                                                 String defaultTopic,
                                                 Map<String, Object> payload,
                                                 Map<String, String> headers) {
        return switch (connectorType) {
            case REST_API -> externalRestClient.invoke(
                    serviceId,
                    "POST",
                    restPath,
                    payload,
                    headers
            );
            case DATA_BUS, MESSAGE_BROKER -> customerMessageBusGateway.publish(
                    requireText(brokerId, "brokerId обязателен для connectorType=" + connectorType.name()),
                    normalizeTopic(topic, defaultTopic),
                    messageKey == null ? "" : messageKey.trim(),
                    payload,
                    headers
            );
        };
    }

    private Map<String, Object> applyResponseScript(Map<String, Object> operationResult,
                                                    Map<String, Object> connectorResponse,
                                                    String responseScriptId,
                                                    Map<String, Object> responseScriptParameters,
                                                    Map<String, Object> responseScriptContext,
                                                    SubjectPrincipal subject) {
        if (responseScriptId == null || responseScriptId.isBlank()) {
            return operationResult;
        }
        if (subject == null) {
            throw new IllegalArgumentException("responseScriptId требует аутентифицированного субъекта");
        }
        Map<String, Object> scriptPayload = new LinkedHashMap<>();
        scriptPayload.put("operationResult", operationResult);
        scriptPayload.put("connectorResponse", connectorResponse);
        Object transformed = groovyScriptService.executeAdvanced(
                responseScriptId.trim(),
                objectMapper.valueToTree(scriptPayload),
                safeMap(responseScriptParameters),
                safeMap(responseScriptContext),
                subject
        );
        return Map.of(
                "operationResult", operationResult,
                "transformedResult", transformed,
                "responseScriptId", responseScriptId.trim()
        );
    }

    private String normalizeTopic(String topic, String fallback) {
        if (topic == null || topic.isBlank()) {
            return fallback;
        }
        return topic.trim();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String requireServiceId(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId обязателен");
        }
        return serviceId.trim();
    }

    private String resolveServiceId(String serviceId, CustomerConnectorType connectorType) {
        if (connectorType == CustomerConnectorType.REST_API) {
            return requireServiceId(serviceId);
        }
        return serviceId == null ? "" : serviceId.trim();
    }

    private String requireIdentifierValue(String identifierValue) {
        if (identifierValue == null || identifierValue.isBlank()) {
            throw new IllegalArgumentException("identifierValue обязателен");
        }
        return identifierValue.trim();
    }

    private String normalizeIdentifierType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "CUSTOM";
        }
        return raw.trim().toUpperCase();
    }

    private String normalizePath(String path, String fallback) {
        if (path == null || path.isBlank()) {
            return fallback;
        }
        String normalized = path.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private Map<String, Object> safeMap(Map<String, Object> source) {
        return source == null ? Map.of() : Map.copyOf(source);
    }

    private List<Map<String, Object>> normalizeCandidateServices(List<Map<String, Object>> services) {
        if (services == null) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> raw : services) {
            Map<String, Object> source = toObjectMap(raw);
            String serviceId = firstNonBlank(source, "serviceId", "id", "serviceCode", "code");
            if (serviceId.isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("serviceId", serviceId);
            item.put("serviceName", firstNonBlank(source, "serviceName", "name", "title"));
            item.put("waitingCount", toBigDecimal(source.get("waitingCount"), toBigDecimal(source.get("queueLength"), java.math.BigDecimal.ZERO)));
            item.put("standardWaitMinutes", toBigDecimal(source.get("standardWaitMinutes"), toBigDecimal(source.get("avgWaitMinutes"), java.math.BigDecimal.ZERO)));
            item.put("predictedWaitMinutes", calculatePredictedWait(source));
            item.put("raw", source);
            normalized.add(item);
        }
        return normalized;
    }

    private List<Map<String, Object>> rankServicesByPredictedWait(List<Map<String, Object>> services) {
        return services.stream()
                .sorted((left, right) -> toBigDecimal(left.get("predictedWaitMinutes"), java.math.BigDecimal.ZERO)
                        .compareTo(toBigDecimal(right.get("predictedWaitMinutes"), java.math.BigDecimal.ZERO)))
                .toList();
    }

    private java.math.BigDecimal calculatePredictedWait(Map<String, Object> service) {
        java.math.BigDecimal waitingCount = toBigDecimal(service.get("waitingCount"), toBigDecimal(service.get("queueLength"), java.math.BigDecimal.ZERO));
        java.math.BigDecimal standardWaitMinutes = toBigDecimal(service.get("standardWaitMinutes"), toBigDecimal(service.get("avgWaitMinutes"), java.math.BigDecimal.ZERO));
        return waitingCount.multiply(standardWaitMinutes);
    }

    private java.math.BigDecimal toBigDecimal(Object raw, java.math.BigDecimal fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(raw).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, Map.class);
        }
        return Map.of();
    }

    private Map<String, String> safeHeaders(Map<String, String> source) {
        return source == null ? Map.of() : Map.copyOf(source);
    }

    private List<String> sanitizeBranches(List<String> branchIds) {
        if (branchIds == null) {
            return List.of();
        }
        return branchIds.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private Map<String, Object> buildPrebookingPayload(String identifierType,
                                                       String identifierValue,
                                                       CustomerPrebookingRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identifier", Map.of(
                "type", identifierType,
                "value", identifierValue
        ));
        payload.put("filters", safeMap(request.filters()));
        if (request.targetServiceId() != null && !request.targetServiceId().isBlank()) {
            payload.put("targetServiceId", request.targetServiceId().trim());
        }
        if (request.branchId() != null && !request.branchId().isBlank()) {
            payload.put("branchId", request.branchId().trim());
        }
        return payload;
    }

    private String maskIdentifier(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        String suffix = value.substring(value.length() - 4);
        return "*".repeat(Math.max(0, value.length() - 4)) + suffix;
    }

    private List<Map<String, Object>> resolveCatalog(CustomerServiceAvailabilityRequest request, SubjectPrincipal subject) {
        if (request.serviceCatalog() != null && !request.serviceCatalog().isEmpty()) {
            return normalizeServiceCatalog(Map.of("services", request.serviceCatalog()));
        }
        if (request.medicalServicesRequest() == null) {
            throw new IllegalArgumentException("medicalServicesRequest обязателен, если serviceCatalog не задан");
        }
        Map<String, Object> source = fetchMedicalServicesWithPrerequisites(request.medicalServicesRequest(), subject);
        Object services = source.get("services");
        if (services instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(this::toObjectMap)
                    .toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeServiceCatalog(Map<String, Object> connectorResponse) {
        List<Map<String, Object>> rawServices = extractServiceMaps(connectorResponse);
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> raw : rawServices) {
            String serviceId = firstNonBlank(raw, "serviceId", "id", "code", "serviceCode");
            if (serviceId.isBlank()) {
                continue;
            }
            String serviceName = firstNonBlank(raw, "serviceName", "name", "title", "displayName");
            Set<String> prerequisites = extractPrerequisites(raw);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("serviceId", serviceId);
            item.put("serviceName", serviceName);
            item.put("prerequisiteServiceIds", List.copyOf(prerequisites));
            item.put("availableNow", prerequisites.isEmpty());
            normalized.add(item);
        }
        return normalized;
    }

    private List<Map<String, Object>> toFlatServices(List<Map<String, Object>> normalizedServices) {
        return normalizedServices.stream()
                .map(item -> {
                    Map<String, Object> flat = new LinkedHashMap<>();
                    flat.put("serviceId", item.getOrDefault("serviceId", ""));
                    flat.put("serviceName", item.getOrDefault("serviceName", ""));
                    return flat;
                })
                .toList();
    }

    private List<Map<String, Object>> normalizeTimeWindows(Map<String, Object> connectorResponse) {
        List<Map<String, Object>> rawWindows = extractTimeWindowMaps(connectorResponse);
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> raw : rawWindows) {
            String startAt = firstNonBlank(raw, "startAt", "start", "from", "slotStart", "startDateTime");
            String endAt = firstNonBlank(raw, "endAt", "end", "to", "slotEnd", "endDateTime");
            if (startAt.isBlank() && endAt.isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("windowId", firstNonBlank(raw, "windowId", "slotId", "id"));
            item.put("serviceId", firstNonBlank(raw, "serviceId", "medicalServiceId", "serviceCode"));
            item.put("branchId", firstNonBlank(raw, "branchId", "branch", "filialId"));
            item.put("doctorId", firstNonBlank(raw, "doctorId", "employeeId", "staffId"));
            item.put("startAt", startAt);
            item.put("endAt", endAt);
            item.put("raw", raw);
            normalized.add(item);
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractTimeWindowMaps(Map<String, Object> connectorResponse) {
        ArrayDeque<Object> queue = new ArrayDeque<>();
        queue.add(connectorResponse);
        while (!queue.isEmpty()) {
            Object current = queue.removeFirst();
            if (current instanceof Map<?, ?> map) {
                Object direct = findTimeWindowArrayCandidate((Map<String, Object>) map);
                if (direct instanceof List<?> list) {
                    return list.stream()
                            .filter(Map.class::isInstance)
                            .map(Map.class::cast)
                            .map(this::toObjectMap)
                            .toList();
                }
                for (Object value : map.values()) {
                    if (value instanceof Map<?, ?> || value instanceof List<?>) {
                        queue.addLast(value);
                    }
                }
            } else if (current instanceof List<?> list) {
                for (Object value : list) {
                    if (value instanceof Map<?, ?> || value instanceof List<?>) {
                        queue.addLast(value);
                    }
                }
            }
        }
        return List.of();
    }

    private Object findTimeWindowArrayCandidate(Map<String, Object> map) {
        List<String> keys = List.of("timeWindows", "windows", "slots", "availableWindows", "appointments", "result");
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof List<?>) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractServiceMaps(Map<String, Object> connectorResponse) {
        ArrayDeque<Object> queue = new ArrayDeque<>();
        queue.add(connectorResponse);
        while (!queue.isEmpty()) {
            Object current = queue.removeFirst();
            if (current instanceof Map<?, ?> map) {
                Object direct = findServiceArrayCandidate((Map<String, Object>) map);
                if (direct instanceof List<?> list) {
                    return list.stream()
                            .filter(Map.class::isInstance)
                            .map(Map.class::cast)
                            .map(this::toObjectMap)
                            .toList();
                }
                for (Object value : map.values()) {
                    if (value instanceof Map<?, ?> || value instanceof List<?>) {
                        queue.addLast(value);
                    }
                }
            } else if (current instanceof List<?> list) {
                for (Object value : list) {
                    if (value instanceof Map<?, ?> || value instanceof List<?>) {
                        queue.addLast(value);
                    }
                }
            }
        }
        return List.of();
    }

    private Object findServiceArrayCandidate(Map<String, Object> map) {
        List<String> keys = List.of("services", "medicalServices", "items", "catalog", "result");
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof List<?>) {
                return value;
            }
        }
        return null;
    }

    private Set<String> extractPrerequisites(Map<String, Object> raw) {
        LinkedHashSet<String> prerequisites = new LinkedHashSet<>();
        List<String> fields = List.of(
                "prerequisiteServiceIds",
                "requiredServiceIds",
                "requiredBefore",
                "dependencies",
                "prerequisites"
        );
        for (String field : fields) {
            prerequisites.addAll(sanitizeIdSet(toStringList(raw.get(field))));
        }
        return prerequisites;
    }

    private String firstNonBlank(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private List<String> toStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        if (value == null) {
            return List.of();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(text);
    }

    private Set<String> sanitizeIdSet(List<String> rawIds) {
        if (rawIds == null) {
            return Set.of();
        }
        Set<String> sanitized = new LinkedHashSet<>();
        for (String raw : rawIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            sanitized.add(raw.trim());
        }
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toObjectMap(Object source) {
        return objectMapper.convertValue(source, Map.class);
    }
}
