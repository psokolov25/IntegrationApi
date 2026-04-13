package ru.aritmos.integration.eventing.visitmanager;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.IntegrationEvent;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
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
        String sourceVisitManagerId = asString(first(payload,
                "meta.visitManagerId",
                "meta.targetVisitManagerId",
                "meta.target_visit_manager_id",
                "metadata.visitManagerId",
                "metadata.targetVisitManagerId",
                "metadata.target_visit_manager_id",
                "data.meta.visitManagerId",
                "data.meta.targetVisitManagerId",
                "data.meta.target_visit_manager_id",
                "data.visitManagerId",
                "data.targetVisitManagerId",
                "visitManagerId",
                "targetVisitManagerId",
                "target_visit_manager_id"));
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
        String canonicalEventId = asString(first(payload,
                "eventId",
                "event_id",
                "meta.eventId",
                "meta.event_id",
                "metadata.eventId",
                "metadata.event_id",
                "data.eventId",
                "data.event_id",
                "data.meta.eventId",
                "data.meta.event_id"));
        if (canonicalEventId == null || canonicalEventId.isBlank()) {
            canonicalEventId = event.eventId();
        }
        return new VisitManagerBranchStateEventPayload(
                sourceVisitManagerId,
                branchId,
                status,
                activeWindow,
                queueSize,
                updatedAt,
                updatedBy,
                canonicalEventId
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
        IntegrationGatewayConfiguration.VisitEventMappingSettings mapping = configuration.getEventing().getVisitEventMapping();
        String branchId = required(payload, safePaths(mapping.getBranchIdPaths()));
        String sourceVisitManagerId = asString(first(payload, safePaths(mapping.getVisitManagerIdPaths())));
        if (sourceVisitManagerId == null || sourceVisitManagerId.isBlank()) {
            sourceVisitManagerId = event.source();
        }
        if (sourceVisitManagerId == null || sourceVisitManagerId.isBlank()) {
            throw new IllegalArgumentException("visitManagerId обязателен (в payload или source события)");
        }
        Instant occurredAt = parseUpdatedAt(first(payload, safePaths(mapping.getOccurredAtPaths())), event.occurredAt());
        String canonicalEventId = asString(first(payload, safePaths(mapping.getEventIdPaths())));
        if (canonicalEventId == null || canonicalEventId.isBlank()) {
            canonicalEventId = event.eventId();
        }
        return new VisitManagerVisitEventPayload(
                sourceVisitManagerId,
                branchId,
                event.eventType(),
                occurredAt,
                canonicalEventId
        );
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
        String canonicalEventId = asString(first(payload,
                "eventId",
                "event_id",
                "meta.eventId",
                "meta.event_id",
                "metadata.eventId",
                "metadata.event_id",
                "data.eventId",
                "data.event_id",
                "data.meta.eventId",
                "data.meta.event_id"));
        if (canonicalEventId == null || canonicalEventId.isBlank()) {
            canonicalEventId = event.eventId();
        }
        return new VisitManagerBranchStateEventPayload(
                sourceVisitManagerId,
                branchId,
                status,
                activeWindow,
                queueSize,
                updatedAt,
                updatedBy,
                canonicalEventId
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
        if (path == null || path.isBlank()) {
            return null;
        }
        if (!path.contains(".")) {
            Object direct = findByNormalizedKey(payload, path);
            if (direct != null) {
                return direct;
            }
            return findPathValue(payload, List.of(path), 0);
        }
        return findPathValue(payload, List.of(path.split("\\.")), 0);
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
        IntegrationGatewayConfiguration.EntityChangedBranchMappingSettings mapping =
                configuration.getEventing().getEntityChangedBranchMapping();
        for (String root : safeList(mapping.getQueueSnapshotRoots(),
                List.of("newValue", "data.entity", "data.branch", "data", "oldValue"))) {
            Object snapshot = byPath(payload, root);
            Integer inferred = inferQueueSizeFromSnapshotNode(snapshot, mapping);
            if (inferred != null) {
                return inferred;
            }
        }
        return null;
    }

    private Integer inferQueueSizeFromSnapshotNode(
            Object snapshotNode,
            IntegrationGatewayConfiguration.EntityChangedBranchMappingSettings mapping
    ) {
        if (!(snapshotNode instanceof Map<?, ?> snapshot)) {
            return null;
        }
        Object servicePointsRaw = firstByKeys(snapshot,
                safeList(mapping.getServicePointsKeys(),
                        List.of("servicePoints", "service_points", "windows", "serviceWindows")));
        Integer fromServicePoints = sumVisitsInContainer(servicePointsRaw);
        if (fromServicePoints != null) {
            return fromServicePoints;
        }
        Object queuesRaw = firstByKeys(snapshot,
                safeList(mapping.getQueuesKeys(), List.of("queues", "queueMap", "queue_map")));
        Integer fromQueues = sumVisitsInContainer(queuesRaw);
        if (fromQueues != null) {
            return fromQueues;
        }
        if (hasAnyKey(snapshot, safeList(mapping.getVisitsKeys(), List.of("visits", "visitList", "visit_list")))) {
            return countVisits(snapshot, mapping);
        }
        return null;
    }

    private int countVisits(
            Object servicePointRaw,
            IntegrationGatewayConfiguration.EntityChangedBranchMappingSettings mapping
    ) {
        if (!(servicePointRaw instanceof Map<?, ?> servicePoint)) {
            return 0;
        }
        Object visitsRaw = firstByKeys(servicePoint,
                safeList(mapping.getVisitsKeys(), List.of("visits", "visitList", "visit_list")));
        if (visitsRaw instanceof java.util.Collection<?> visits) {
            return visits.size();
        }
        if (visitsRaw instanceof Map<?, ?> visits) {
            return visits.size();
        }
        return 0;
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
        Instant epochParsed = parseEpochUpdatedAt(value);
        if (epochParsed != null) {
            return epochParsed;
        }
        String text = String.valueOf(value);
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // try other common representations from external systems
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // continue
        }
        try {
            return ZonedDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("payload.updatedAt должен быть в ISO-8601");
        }
    }


    private Instant parseEpochUpdatedAt(Object value) {
        if (value instanceof Number number) {
            long epoch = number.longValue();
            return epoch > 9_999_999_999L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || !text.matches("^-?\\d{10,16}$")) {
            return null;
        }
        try {
            long epoch = Long.parseLong(text);
            return epoch > 9_999_999_999L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Object findPathValue(Object current, List<String> parts, int partIdx) {
        if (current == null) {
            return null;
        }
        if (partIdx >= parts.size()) {
            return current;
        }
        String part = parts.get(partIdx);
        if (current instanceof Map<?, ?> map) {
            if ("*".equals(part)) {
                for (Object value : map.values()) {
                    Object wildcardNested = findPathValue(value, parts, partIdx + 1);
                    if (wildcardNested != null) {
                        return wildcardNested;
                    }
                }
                return null;
            }
            Object next = findByNormalizedKey(map, part);
            if (next != null) {
                return findPathValue(next, parts, partIdx + 1);
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !isWrapperKey(key)) {
                    continue;
                }
                Object nested = findPathValue(entry.getValue(), parts, partIdx);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (current instanceof Collection<?> collection) {
            if ("*".equals(part)) {
                for (Object item : collection) {
                    Object wildcardNested = findPathValue(item, parts, partIdx + 1);
                    if (wildcardNested != null) {
                        return wildcardNested;
                    }
                }
                return null;
            }
            Integer index = parseIndex(part);
            if (index != null && index >= 0 && index < collection.size()) {
                Object indexed = collection.stream().skip(index).findFirst().orElse(null);
                return findPathValue(indexed, parts, partIdx + 1);
            }
            for (Object item : collection) {
                Object nested = findPathValue(item, parts, partIdx);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private Integer parseIndex(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Object findByNormalizedKey(Map<?, ?> map, String expectedKey) {
        if (map.containsKey(expectedKey)) {
            return map.get(expectedKey);
        }
        String normalizedExpected = normalizeKey(expectedKey);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            if (normalizeKey(key).equals(normalizedExpected)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").toLowerCase();
    }

    private boolean isWrapperKey(String key) {
        IntegrationGatewayConfiguration.EntityChangedBranchMappingSettings mapping =
                configuration.getEventing().getEntityChangedBranchMapping();
        List<String> configured = safeList(mapping.getWrapperKeys(), List.of(
                "data", "payload", "entity", "entities", "event", "message", "content", "body",
                "detail", "item", "items", "result", "snapshot", "newvalue", "oldvalue", "branch"
        ));
        String normalized = normalizeKey(key);
        return configured.stream()
                .map(this::normalizeKey)
                .anyMatch(item -> item.equals(normalized));
    }

    private Object firstByKeys(Map<?, ?> map, String... keys) {
        return firstByKeys(map, List.of(keys));
    }

    private Object firstByKeys(Map<?, ?> map, List<String> keys) {
        for (String key : keys) {
            Object value = findByNormalizedKey(map, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer sumVisitsInContainer(Object container) {
        IntegrationGatewayConfiguration.EntityChangedBranchMappingSettings mapping =
                configuration.getEventing().getEntityChangedBranchMapping();
        if (container instanceof Map<?, ?> map) {
            int total = 0;
            boolean hasAny = false;
            for (Object item : map.values()) {
                total += countVisits(item, mapping);
                hasAny = true;
            }
            return hasAny ? total : 0;
        }
        if (container instanceof Collection<?> collection) {
            int total = 0;
            for (Object item : collection) {
                total += countVisits(item, mapping);
            }
            return total;
        }
        return null;
    }

    private boolean hasAnyKey(Map<?, ?> map, List<String> keys) {
        for (String key : keys) {
            if (findByNormalizedKey(map, key) != null) {
                return true;
            }
        }
        return false;
    }

    private List<String> safeList(List<String> configured, List<String> defaults) {
        if (configured == null || configured.isEmpty()) {
            return defaults;
        }
        return configured.stream().filter(Objects::nonNull).toList();
    }
}
