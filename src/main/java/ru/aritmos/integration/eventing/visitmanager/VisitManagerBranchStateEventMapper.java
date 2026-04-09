package ru.aritmos.integration.eventing.visitmanager;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.IntegrationEvent;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * Маппер DataBus-событий VisitManager к канонической модели branch-state.
 */
@Singleton
public class VisitManagerBranchStateEventMapper {

    private final IntegrationGatewayConfiguration configuration;

    public VisitManagerBranchStateEventMapper() {
        this(new IntegrationGatewayConfiguration());
    }

    public VisitManagerBranchStateEventMapper(IntegrationGatewayConfiguration configuration) {
        this.configuration = configuration;
    }

    public VisitManagerBranchStateEventPayload map(IntegrationEvent event) {
        Map<String, Object> payload = event.payload();
        if (payload == null) {
            throw new IllegalArgumentException("payload обязателен для branch-state-updated");
        }
        String branchId = required(payload, "data.branch.id", "data.branchId", "data.branch_id", "branchId", "branch_id");
        String sourceVisitManagerId = asString(first(payload, "meta.visitManagerId", "metadata.visitManagerId", "data.visitManagerId",
                "data.targetVisitManagerId", "visitManagerId", "targetVisitManagerId", "target_visit_manager_id"));
        if (sourceVisitManagerId == null || sourceVisitManagerId.isBlank()) {
            sourceVisitManagerId = event.source();
        }
        if (sourceVisitManagerId == null || sourceVisitManagerId.isBlank()) {
            throw new IllegalArgumentException("visitManagerId обязателен (в payload или source события)");
        }
        String status = required(payload, "data.state.status", "data.state.code", "data.status", "status", "state");
        String activeWindow = required(payload, "data.state.activeWindow", "data.state.active_window",
                "data.activeWindow", "activeWindow", "active_window");
        int queueSize = parseQueueSize(first(payload, "data.state.queueSize", "data.queueSize", "queueSize", "queue_size"));
        Instant updatedAt = parseUpdatedAt(first(payload, "data.state.updatedAt", "data.updatedAt", "updatedAt", "updated_at"), event.occurredAt());
        String updatedBy = asString(first(payload, "data.state.updatedBy", "data.updatedBy", "updatedBy", "updated_by"));
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = event.source();
        }
        return new VisitManagerBranchStateEventPayload(
                sourceVisitManagerId,
                branchId,
                status,
                activeWindow,
                queueSize,
                updatedAt,
                updatedBy
        );
    }

    /**
     * Извлекает из события визита минимальные атрибуты для refresh branch-state.
     */
    public VisitManagerVisitEventPayload mapVisitEvent(IntegrationEvent event) {
        Map<String, Object> payload = event.payload();
        if (payload == null) {
            throw new IllegalArgumentException("payload обязателен для VisitManager visit-event");
        }
        String branchId = required(payload, "branchId", "data.branchId", "visit.branchId", "data.visit.branchId");
        String sourceVisitManagerId = asString(first(payload, "meta.visitManagerId", "metadata.visitManagerId",
                "data.visitManagerId", "visitManagerId", "targetVisitManagerId"));
        if (sourceVisitManagerId == null || sourceVisitManagerId.isBlank()) {
            sourceVisitManagerId = event.source();
        }
        if (sourceVisitManagerId == null || sourceVisitManagerId.isBlank()) {
            throw new IllegalArgumentException("visitManagerId обязателен (в payload или source события)");
        }
        return new VisitManagerVisitEventPayload(sourceVisitManagerId, branchId, event.eventType());
    }

    /**
     * Проверяет, что событие соответствует ENTITY_CHANGED для Branch по правилам из конфигурации.
     */
    public boolean supportsBranchEntityChangedEventType(String eventType) {
        IntegrationGatewayConfiguration.EntityChangedBranchMappingSettings mapping =
                configuration.getEventing().getEntityChangedBranchMapping();
        if (!mapping.isEnabled()) {
            return false;
        }
        String configuredType = mapping.getEventType();
        if (configuredType == null || configuredType.isBlank()) {
            configuredType = "ENTITY_CHANGED";
        }
        return configuredType.equalsIgnoreCase(eventType);
    }

    /**
     * Проверяет, что событие соответствует ENTITY_CHANGED для Branch по правилам из конфигурации.
     */
    public boolean isBranchEntityChanged(IntegrationEvent event) {
        if (!supportsBranchEntityChangedEventType(event.eventType())) {
            return false;
        }
        IntegrationGatewayConfiguration.EntityChangedBranchMappingSettings mapping =
                configuration.getEventing().getEntityChangedBranchMapping();
        Map<String, Object> payload = event.payload();
        if (payload == null) {
            return false;
        }
        Object classValue = first(payload, safePaths(mapping.getClassNamePaths()));
        if (classValue == null) {
            return false;
        }
        String normalized = normalizedClassName(classValue);
        if (normalized == null) {
            return false;
        }
        return mapping.getAcceptedClassNames().stream()
                .filter(Objects::nonNull)
                .map(this::normalizedClassName)
                .filter(Objects::nonNull)
                .anyMatch(item -> item.equalsIgnoreCase(normalized));
    }

    /**
     * Маппинг ENTITY_CHANGED(Branch) в каноническое состояние branch-state.
     */
    public VisitManagerBranchStateEventPayload mapEntityChangedBranch(IntegrationEvent event) {
        IntegrationGatewayConfiguration.EntityChangedBranchMappingSettings mapping =
                configuration.getEventing().getEntityChangedBranchMapping();
        Map<String, Object> payload = event.payload();
        if (payload == null) {
            throw new IllegalArgumentException("payload обязателен для ENTITY_CHANGED(Branch)");
        }
        String branchId = required(payload, safePaths(mapping.getBranchIdPaths()));
        String sourceVisitManagerId = asString(first(payload, safePaths(mapping.getVisitManagerIdPaths())));
        if (sourceVisitManagerId == null || sourceVisitManagerId.isBlank()) {
            sourceVisitManagerId = event.source();
        }
        if (sourceVisitManagerId == null || sourceVisitManagerId.isBlank()) {
            throw new IllegalArgumentException("visitManagerId обязателен (в payload или source события)");
        }
        String status = withFallback(
                asString(first(payload, safePaths(mapping.getStatusPaths()))),
                inferStatus(payload),
                "UNKNOWN"
        );
        String activeWindow = withFallback(
                asString(first(payload, safePaths(mapping.getActiveWindowPaths()))),
                "UNSPECIFIED"
        );
        int queueSize = parseQueueSizeWithFallback(
                first(payload, safePaths(mapping.getQueueSizePaths())),
                inferQueueSizeFromBranchSnapshot(payload)
        );
        Instant updatedAt = parseUpdatedAt(first(payload, safePaths(mapping.getUpdatedAtPaths())), event.occurredAt());
        String updatedBy = asString(first(payload, safePaths(mapping.getUpdatedByPaths())));
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = event.source();
        }
        return new VisitManagerBranchStateEventPayload(
                sourceVisitManagerId,
                branchId,
                status,
                activeWindow,
                queueSize,
                updatedAt,
                updatedBy
        );
    }

    private String required(Map<String, Object> payload, String... paths) {
        String value = asString(first(payload, paths));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Одно из обязательных полей отсутствует: " + String.join(", ", paths));
        }
        return value;
    }

    private Object first(Map<String, Object> payload, String... paths) {
        for (String path : paths) {
            Object value = byPath(payload, path);
            if (value != null && !Objects.toString(value, "").isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String[] safePaths(java.util.List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return new String[0];
        }
        return paths.stream()
                .filter(Objects::nonNull)
                .toArray(String[]::new);
    }

    @SuppressWarnings("unchecked")
    private Object byPath(Map<String, Object> payload, String path) {
        if (!path.contains(".")) {
            return payload.get(path);
        }
        Object current = payload;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String withFallback(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizedClassName(Object value) {
        String className = asString(value);
        if (className == null || className.isBlank()) {
            return null;
        }
        int dotIdx = className.lastIndexOf('.');
        if (dotIdx < 0) {
            return className.trim();
        }
        return className.substring(dotIdx + 1).trim();
    }

    private int parseQueueSize(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        try {
            return Math.max(Integer.parseInt(String.valueOf(value)), 0);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("payload.queueSize должен быть целым числом");
        }
    }

    private int parseQueueSizeWithFallback(Object primary, Integer fallback) {
        if (primary != null) {
            return parseQueueSize(primary);
        }
        if (fallback != null) {
            return Math.max(fallback, 0);
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Integer inferQueueSizeFromBranchSnapshot(Map<String, Object> payload) {
        Object newValue = payload.get("newValue");
        if (!(newValue instanceof Map<?, ?> branchSnapshot)) {
            return null;
        }
        Object servicePointsRaw = ((Map<String, Object>) branchSnapshot).get("servicePoints");
        if (!(servicePointsRaw instanceof Map<?, ?> servicePoints)) {
            return null;
        }
        int total = 0;
        for (Object servicePointRaw : servicePoints.values()) {
            if (!(servicePointRaw instanceof Map<?, ?> servicePoint)) {
                continue;
            }
            Object visitsRaw = ((Map<String, Object>) servicePoint).get("visits");
            if (visitsRaw instanceof java.util.Collection<?> visits) {
                total += visits.size();
            }
        }
        return total;
    }

    private String inferStatus(Map<String, Object> payload) {
        String action = asString(payload.get("action"));
        if (action == null || action.isBlank()) {
            return null;
        }
        String normalized = action.trim().toUpperCase();
        if (normalized.contains("DELETE") || normalized.contains("CLOSE")) {
            return "CLOSED";
        }
        return null;
    }

    private Instant parseUpdatedAt(Object value, Instant fallback) {
        if (value == null) {
            return fallback == null ? Instant.now() : fallback;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("payload.updatedAt должен быть в ISO-8601");
        }
    }
}
