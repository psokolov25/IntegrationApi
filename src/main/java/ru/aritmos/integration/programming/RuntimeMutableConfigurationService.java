package ru.aritmos.integration.programming;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.EventingInboxOutboxStorage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Управление изменяемыми runtime-настройками службы с персистентностью в выбранном storage.
 */
@Singleton
public class RuntimeMutableConfigurationService {

    private final IntegrationGatewayConfiguration configuration;
    private final EventingInboxOutboxStorage storage;
    private final Map<String, Object> defaults;

    public RuntimeMutableConfigurationService(IntegrationGatewayConfiguration configuration,
                                             EventingInboxOutboxStorage storage) {
        this.configuration = configuration;
        this.storage = storage;
        this.defaults = captureCurrentSettings();
    }

    @PostConstruct
    void init() {
        Map<String, Object> saved = storage.loadRuntimeSettings();
        if (saved != null && !saved.isEmpty()) {
            apply(saved, false);
        }
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(captureCurrentSettings());
    }

    public Map<String, Object> apply(Map<String, Object> requested) {
        return apply(requested, true);
    }

    public Map<String, Object> resetToDefaults() {
        return apply(defaults, true);
    }

    public void persistCurrent() {
        storage.saveRuntimeSettings(captureCurrentSettings());
    }

    private Map<String, Object> apply(Map<String, Object> requested, boolean persist) {
        if (requested == null || requested.isEmpty()) {
            Map<String, Object> current = captureCurrentSettings();
            if (persist) {
                storage.saveRuntimeSettings(current);
            }
            return Map.copyOf(current);
        }
        configuration.setAggregateMaxBranches(requirePositive(
                intParam(requested.get("aggregateMaxBranches"), configuration.getAggregateMaxBranches()),
                "aggregateMaxBranches"
        ));
        configuration.setAggregateRequestTimeoutMillis(requirePositive(
                intParam(requested.get("aggregateRequestTimeoutMillis"), (int) configuration.getAggregateRequestTimeoutMillis()),
                "aggregateRequestTimeoutMillis"
        ));
        configuration.getEventing().setOutboxBackoffSeconds(requirePositive(
                intParam(requested.get("outboxBackoffSeconds"), configuration.getEventing().getOutboxBackoffSeconds()),
                "outboxBackoffSeconds"
        ));
        configuration.getEventing().setOutboxMaxAttempts(requirePositive(
                intParam(requested.get("outboxMaxAttempts"), configuration.getEventing().getOutboxMaxAttempts()),
                "outboxMaxAttempts"
        ));
        configuration.getEventing().setInboxProcessingTimeoutSeconds(requirePositive(
                intParam(requested.get("inboxProcessingTimeoutSeconds"), configuration.getEventing().getInboxProcessingTimeoutSeconds()),
                "inboxProcessingTimeoutSeconds"
        ));
        configuration.getEventing().setOutboxAutoFlushBatchSize(requirePositive(
                intParam(requested.get("outboxAutoFlushBatchSize"), configuration.getEventing().getOutboxAutoFlushBatchSize()),
                "outboxAutoFlushBatchSize"
        ));
        configuration.getEventing().setMaxPayloadFields(requirePositive(
                intParam(requested.get("maxPayloadFields"), configuration.getEventing().getMaxPayloadFields()),
                "maxPayloadFields"
        ));
        Map<String, Object> httpProcessing = objectMapParam(requested.get("httpProcessing"));
        IntegrationGatewayConfiguration.HttpProcessingSettings target = configuration.getProgrammableApi().getHttpProcessing();
        if (!httpProcessing.isEmpty()) {
            target.setEnabled(boolParam(httpProcessing.get("enabled"), target.isEnabled()));
            target.setAddDirectionHeader(boolParam(httpProcessing.get("addDirectionHeader"), target.isAddDirectionHeader()));
            target.setDirectionHeaderName(strParam(httpProcessing.get("directionHeaderName"), target.getDirectionHeaderName()));
            target.setRequestEnvelopeEnabled(boolParam(httpProcessing.get("requestEnvelopeEnabled"), target.isRequestEnvelopeEnabled()));
            target.setParseJsonBody(boolParam(httpProcessing.get("parseJsonBody"), target.isParseJsonBody()));
            target.setResponseBodyMaxChars(requirePositive(
                    intParam(httpProcessing.get("responseBodyMaxChars"), target.getResponseBodyMaxChars()),
                    "httpProcessing.responseBodyMaxChars"
            ));
        }
        Map<String, Object> updated = captureCurrentSettings();
        if (persist) {
            storage.saveRuntimeSettings(updated);
        }
        return Map.copyOf(updated);
    }

    private Map<String, Object> captureCurrentSettings() {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("securityMode", configuration.getSecurityMode().name());
        current.put("anonymousAccessEnabled", configuration.getAnonymousAccess().isEnabled());
        current.put("programmableApiEnabled", configuration.getProgrammableApi().isEnabled());
        current.put("eventingEnabled", configuration.getEventing().isEnabled());
        current.put("aggregateMaxBranches", configuration.getAggregateMaxBranches());
        current.put("aggregateRequestTimeoutMillis", configuration.getAggregateRequestTimeoutMillis());
        current.put("outboxBackoffSeconds", configuration.getEventing().getOutboxBackoffSeconds());
        current.put("outboxMaxAttempts", configuration.getEventing().getOutboxMaxAttempts());
        current.put("inboxProcessingTimeoutSeconds", configuration.getEventing().getInboxProcessingTimeoutSeconds());
        current.put("outboxAutoFlushBatchSize", configuration.getEventing().getOutboxAutoFlushBatchSize());
        current.put("maxPayloadFields", configuration.getEventing().getMaxPayloadFields());
        current.put("branchStateCacheTtl", configuration.getBranchStateCacheTtl().toString());
        current.put("branchStateEventRefreshDebounce", configuration.getBranchStateEventRefreshDebounce().toString());
        current.put("httpProcessing", Map.of(
                "enabled", configuration.getProgrammableApi().getHttpProcessing().isEnabled(),
                "addDirectionHeader", configuration.getProgrammableApi().getHttpProcessing().isAddDirectionHeader(),
                "directionHeaderName", configuration.getProgrammableApi().getHttpProcessing().getDirectionHeaderName(),
                "requestEnvelopeEnabled", configuration.getProgrammableApi().getHttpProcessing().isRequestEnvelopeEnabled(),
                "responseBodyMaxChars", configuration.getProgrammableApi().getHttpProcessing().getResponseBodyMaxChars(),
                "parseJsonBody", configuration.getProgrammableApi().getHttpProcessing().isParseJsonBody()
        ));
        return current;
    }

    private int intParam(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean boolParam(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private String strParam(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMapParam(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, val) -> {
            String k = key == null ? "" : String.valueOf(key).trim();
            if (!k.isBlank()) {
                normalized.put(k, val);
            }
        });
        return Map.copyOf(normalized);
    }

    private int requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " должен быть > 0");
        }
        return value;
    }
}
