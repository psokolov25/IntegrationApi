package ru.aritmos.integration.security.core;

import jakarta.inject.Singleton;

/**
 * Сервис проверки прав доступа.
 */
@Singleton
public class AuthorizationService {

    public void requirePermission(SubjectPrincipal subject, String permission) {
        if (subject == null || subject.permissions() == null || !subject.permissions().contains(permission)) {
            throw new SecurityException("Недостаточно прав: требуется permission " + permission);
        }
    }
}
