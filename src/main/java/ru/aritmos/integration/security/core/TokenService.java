package ru.aritmos.integration.security.core;

import java.util.Optional;

/**
 * Сервис выдачи и валидации токенов.
 */
public interface TokenService {

    String issueToken(String subjectId, String secret, java.util.Set<String> permissions);

    Optional<SubjectPrincipal> parseToken(String token);
}
