package ru.aritmos.integration.eventing;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

/**
 * Периодически отправляет pending/failed outbox-сообщения в фоновом режиме.
 */
@Singleton
public class EventOutboxAutoFlushScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(EventOutboxAutoFlushScheduler.class);

    private final EventOutboxFlusher outboxFlusher;
    private final IntegrationGatewayConfiguration configuration;

    public EventOutboxAutoFlushScheduler(EventOutboxFlusher outboxFlusher,
                                         IntegrationGatewayConfiguration configuration) {
        this.outboxFlusher = outboxFlusher;
        this.configuration = configuration;
    }

    @Scheduled(
            fixedDelay = "${integration.eventing.outbox-auto-flush-interval:30s}",
            initialDelay = "${integration.eventing.outbox-auto-flush-initial-delay:10s}"
    )
    void flush() {
        if (!configuration.getEventing().isOutboxAutoFlushEnabled()) {
            return;
        }
        int batchSize = Math.max(1, configuration.getEventing().getOutboxAutoFlushBatchSize());
        int processed = outboxFlusher.flushOutbox(batchSize).size();
        if (processed > 0) {
            LOG.info("EVENT_OUTBOX_AUTO_FLUSH processed={} batchSize={}", processed, batchSize);
        }
    }
}
