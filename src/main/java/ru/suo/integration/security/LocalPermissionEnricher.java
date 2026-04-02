package ru.suo.integration.security;

import jakarta.inject.Singleton;
import ru.suo.integration.config.IntegrationGatewayConfiguration;
import ru.suo.integration.security.core.SubjectPrincipal;

import java.util.HashSet;

/**
 * Локальное дообогащение прав субъекта для HYBRID режима.
 */
@Singleton
public class LocalPermissionEnricher {

    private final IntegrationGatewayConfiguration configuration;

    public LocalPermissionEnricher(IntegrationGatewayConfiguration configuration) {
        this.configuration = configuration;
    }

    public SubjectPrincipal enrich(SubjectPrincipal subject) {
        var additional = configuration.getLocalSubjectPermissions().getOrDefault(subject.subjectId(), java.util.Set.of());
        if (additional.isEmpty()) {
            return subject;
        }
        var merged = new HashSet<>(subject.permissions());
        merged.addAll(additional);
        return new SubjectPrincipal(subject.subjectId(), merged);
    }
}
