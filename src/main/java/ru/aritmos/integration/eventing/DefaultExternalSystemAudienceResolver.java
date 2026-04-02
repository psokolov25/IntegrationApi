package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Базовый resolver аудиторий внешних систем заказчика (АРМ/приемная).
 */
@Singleton
public class DefaultExternalSystemAudienceResolver implements ExternalSystemAudienceResolver {

    private static final Set<String> BRANCH_STATE_AUDIENCE = Set.of("employee-workplace", "reception-desk");
    private static final String VISIT_EVENT_PREFIX = "VISIT_";

    @Override
    public Set<String> resolve(IntegrationEvent event) {
        LinkedHashSet<String> audience = new LinkedHashSet<>();
        audience.addAll(resolveFromPayload(event.payload()));
        audience.addAll(resolveByType(event.eventType()));
        return Set.copyOf(audience);
    }

    private Set<String> resolveByType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return Set.of();
        }
        if ("branch-state-updated".equalsIgnoreCase(eventType)) {
            return BRANCH_STATE_AUDIENCE;
        }
        if (eventType.toUpperCase().startsWith(VISIT_EVENT_PREFIX)) {
            return BRANCH_STATE_AUDIENCE;
        }
        return Set.of();
    }

    @SuppressWarnings("unchecked")
    private Set<String> resolveFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return Set.of();
        }
        Object metaObject = payload.get("meta");
        if (!(metaObject instanceof Map<?, ?> meta)) {
            return Set.of();
        }
        Object targetSystems = ((Map<String, Object>) meta).get("targetSystems");
        if (targetSystems instanceof Iterable<?> iterable) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (Object item : iterable) {
                String normalized = normalize(item);
                if (normalized != null) {
                    result.add(normalized);
                }
            }
            return result;
        }
        if (targetSystems instanceof Object[] values) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (Object item : values) {
                String normalized = normalize(item);
                if (normalized != null) {
                    result.add(normalized);
                }
            }
            return result;
        }
        String single = normalize(targetSystems);
        return single == null ? Set.of() : Set.of(single);
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = Objects.toString(value, "").trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
