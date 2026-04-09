package ru.aritmos.integration.programming;

import java.time.Instant;

/**
 * Скрипт, хранимый в Redis/in-memory для programmable API.
 */
public record StoredGroovyScript(
        String scriptId,
        GroovyScriptType type,
        String scriptBody,
        String description,
        Instant updatedAt,
        String updatedBy
) {
}
