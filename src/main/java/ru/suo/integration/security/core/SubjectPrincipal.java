package ru.suo.integration.security.core;

import io.micronaut.core.annotation.Introspected;

import java.util.Set;

/**
 * Аутентифицированный субъект с эффективными permission.
 */
@Introspected
public record SubjectPrincipal(String subjectId, Set<String> permissions) {
}
