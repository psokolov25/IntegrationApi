package ru.aritmos.integration.domain;

import ru.aritmos.integration.programming.GroovyScriptType;

/**
 * Запрос на создание/обновление Groovy-скрипта.
 */
public record GroovyScriptUpsertRequest(
        GroovyScriptType type,
        String description,
        String scriptBody
) {
}
