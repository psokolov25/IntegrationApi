package ru.suo.integration.security;

import io.micronaut.http.HttpRequest;
import ru.suo.integration.security.core.SubjectPrincipal;

import java.util.Optional;

/**
 * Утилита хранения/чтения субъекта в атрибутах запроса.
 */
public final class RequestSecurityContext {

    public static final CharSequence SUBJECT_ATTR = "integration.subject";

    private RequestSecurityContext() {
    }

    public static void attach(HttpRequest<?> request, SubjectPrincipal subject) {
        request.setAttribute(SUBJECT_ATTR, subject);
    }

    public static Optional<SubjectPrincipal> current(HttpRequest<?> request) {
        return request.getAttribute(SUBJECT_ATTR, SubjectPrincipal.class);
    }
}
