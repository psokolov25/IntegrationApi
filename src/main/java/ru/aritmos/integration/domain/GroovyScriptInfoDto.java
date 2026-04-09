package ru.aritmos.integration.domain;

import ru.aritmos.integration.programming.GroovyScriptType;

import java.time.Instant;

/**
 * Метаданные сохраненного Groovy-скрипта.
 */
public record GroovyScriptInfoDto(
        String scriptId,
        GroovyScriptType type,
        String description,
        String scriptBody,
        Instant updatedAt,
        String updatedBy
) {
}
