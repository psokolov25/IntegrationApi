package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;
import ru.aritmos.integration.domain.CustomerLookupRequest;
import ru.aritmos.integration.domain.CustomerMedicalServicesRequest;
import ru.aritmos.integration.domain.CustomerPrebookingRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реализация CRM integration gateway поверх существующего programmable REST connector.
 */
@Singleton
public class ExternalRestCustomerCrmIntegrationGateway implements CustomerCrmIntegrationGateway {

    private static final String IDENTIFY_PATH = "/api/v1/customers/identify";
    private static final String SERVICES_PATH = "/api/v1/medical-services/available";
    private static final String PREBOOKING_PATH = "/api/v1/pre-appointments";

    private final ExternalRestClient externalRestClient;

    public ExternalRestCustomerCrmIntegrationGateway(ExternalRestClient externalRestClient) {
        this.externalRestClient = externalRestClient;
    }

    @Override
    public Map<String, Object> identifyClient(CustomerLookupRequest request) {
        String serviceId = requireServiceId(request.serviceId());
        String identifierType = normalizeIdentifierType(request.identifierType());
        String identifierValue = requireIdentifierValue(request.identifierValue());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identifier", Map.of(
                "type", identifierType,
                "value", identifierValue
        ));
        payload.put("filters", safeMap(request.filters()));
        Map<String, Object> response = externalRestClient.invoke(
                serviceId,
                "POST",
                normalizePath(request.path(), IDENTIFY_PATH),
                payload,
                safeHeaders(request.headers())
        );
        return Map.of(
                "operation", "IDENTIFY_CLIENT_IN_EXTERNAL_CRM",
                "serviceId", serviceId,
                "identifierType", identifierType,
                "identifierValueMasked", maskIdentifier(identifierValue),
                "result", response
        );
    }

    @Override
    public Map<String, Object> fetchAvailableMedicalServices(CustomerMedicalServicesRequest request) {
        String serviceId = requireServiceId(request.serviceId());
        String identifierType = normalizeIdentifierType(request.identifierType());
        String identifierValue = requireIdentifierValue(request.identifierValue());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identifier", Map.of(
                "type", identifierType,
                "value", identifierValue
        ));
        payload.put("branchIds", sanitizeBranches(request.branchIds()));
        payload.put("filters", safeMap(request.filters()));
        Map<String, Object> response = externalRestClient.invoke(
                serviceId,
                "POST",
                normalizePath(request.path(), SERVICES_PATH),
                payload,
                safeHeaders(request.headers())
        );
        return Map.of(
                "operation", "FETCH_AVAILABLE_MEDICAL_SERVICES",
                "serviceId", serviceId,
                "identifierType", identifierType,
                "identifierValueMasked", maskIdentifier(identifierValue),
                "result", response
        );
    }

    @Override
    public Map<String, Object> fetchPrebookingData(CustomerPrebookingRequest request) {
        String serviceId = requireServiceId(request.serviceId());
        String identifierType = normalizeIdentifierType(request.identifierType());
        String identifierValue = requireIdentifierValue(request.identifierValue());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identifier", Map.of(
                "type", identifierType,
                "value", identifierValue
        ));
        payload.put("filters", safeMap(request.filters()));
        Map<String, Object> response = externalRestClient.invoke(
                serviceId,
                "POST",
                normalizePath(request.path(), PREBOOKING_PATH),
                payload,
                safeHeaders(request.headers())
        );
        return Map.of(
                "operation", "FETCH_PREBOOKING_DATA",
                "serviceId", serviceId,
                "identifierType", identifierType,
                "identifierValueMasked", maskIdentifier(identifierValue),
                "result", response
        );
    }

    private String requireServiceId(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId обязателен");
        }
        return serviceId.trim();
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

    private String maskIdentifier(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        String suffix = value.substring(value.length() - 4);
        return "*".repeat(Math.max(0, value.length() - 4)) + suffix;
    }
}

