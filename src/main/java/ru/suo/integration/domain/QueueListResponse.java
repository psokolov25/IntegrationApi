package ru.suo.integration.domain;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Ответ списка очередей по филиалу.
 */
@Introspected
public record QueueListResponse(String branchId, String sourceVisitManagerId, List<QueueItemDto> queues, boolean cached) {
}
