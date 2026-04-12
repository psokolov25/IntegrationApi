package ru.aritmos.integration.eventing;

import io.micronaut.context.annotation.Primary;
import jakarta.inject.Singleton;

/**
 * Композитный транспорт: публикует событие во все включенные transport-адаптеры.
 */
@Primary
@Singleton
public class CompositeEventTransportAdapter implements EventTransportAdapter {

    private final KafkaDataBusTransportAdapter kafkaAdapter;
    private final HttpWebhookEventTransportAdapter webhookAdapter;

    public CompositeEventTransportAdapter(KafkaDataBusTransportAdapter kafkaAdapter,
                                          HttpWebhookEventTransportAdapter webhookAdapter) {
        this.kafkaAdapter = kafkaAdapter;
        this.webhookAdapter = webhookAdapter;
    }

    @Override
    public void publish(IntegrationEvent event) {
        RuntimeException firstError = null;
        try {
            kafkaAdapter.publish(event);
        } catch (RuntimeException ex) {
            firstError = ex;
        }
        try {
            webhookAdapter.publish(event);
        } catch (RuntimeException ex) {
            if (firstError == null) {
                firstError = ex;
            } else {
                firstError.addSuppressed(ex);
            }
        }
        if (firstError != null) {
            throw firstError;
        }
    }
}
