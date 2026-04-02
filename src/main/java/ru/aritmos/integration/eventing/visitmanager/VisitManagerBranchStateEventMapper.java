package ru.aritmos.integration.eventing.visitmanager;

import jakarta.inject.Singleton;
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
