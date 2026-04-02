package ru.suo.integration.security.core;

import io.micronaut.http.HttpRequest;

import java.util.Optional;

/**
 * Абстракция аутентификации входящих запросов.
 */
public interface AuthenticationService {

    Optional<SubjectPrincipal> authenticate(HttpRequest<?> request);
}
