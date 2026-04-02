package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;

import java.time.Instant;

/**
 * Состояние отделения в VisitManager для внешних пультов/приемной.
 */
@Introspected
public record BranchStateDto(
        String branchId,
        String sourceVisitManagerId,
        String status,
        String activeWindow,
        int queueSize,
        Instant updatedAt,
        boolean cached,
        String updatedBy
) {
}
