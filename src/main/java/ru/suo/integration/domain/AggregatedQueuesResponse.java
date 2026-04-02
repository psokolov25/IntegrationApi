package ru.suo.integration.domain;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Агрегированный ответ по нескольким филиалам с partial availability.
 */
@Introspected
public record AggregatedQueuesResponse(
        List<QueueListResponse> successful,
        List<BranchError> failed,
        boolean partial
) {
    @Introspected
    public record BranchError(String branchId, String target, String message) {
    }
}
