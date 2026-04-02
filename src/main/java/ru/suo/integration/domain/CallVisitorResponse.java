package ru.suo.integration.domain;

import io.micronaut.core.annotation.Introspected;

/**
 * Результат вызова посетителя в VisitManager.
 */
@Introspected
public record CallVisitorResponse(String visitId, String status, String sourceVisitManagerId) {
}
