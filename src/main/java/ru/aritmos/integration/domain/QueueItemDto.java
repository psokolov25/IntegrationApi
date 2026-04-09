package ru.aritmos.integration.domain;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Унифицированная модель очереди для внешнего API.
 */
@Introspected
@Schema(description = "Элемент списка очередей.")
public record QueueItemDto(
        @Schema(description = "Идентификатор очереди.", example = "Q-101")
        String queueId,
        @Schema(description = "Название очереди.", example = "Кредитные карты")
        String queueName,
        @Schema(description = "Количество ожидающих в очереди.", example = "7")
        int waitingCount
) {
}
