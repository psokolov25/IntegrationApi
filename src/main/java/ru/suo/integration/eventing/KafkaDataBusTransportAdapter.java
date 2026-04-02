package ru.suo.integration.eventing;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.suo.integration.config.IntegrationGatewayConfiguration;

/**
 * Базовый Kafka/DataBus transport adapter (логирующий placeholder для этапа 6.2).
 */
@Singleton
public class KafkaDataBusTransportAdapter implements EventTransportAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaDataBusTransportAdapter.class);
    private final IntegrationGatewayConfiguration configuration;

    public KafkaDataBusTransportAdapter(IntegrationGatewayConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void publish(IntegrationEvent event) {
        if (!configuration.getEventing().getKafka().isEnabled()) {
            return;
        }
        LOG.info("EVENT_PUBLISHED topic={} eventId={} type={}",
                configuration.getEventing().getKafka().getOutboundTopic(),
                event.eventId(),
                event.eventType());
    }
}
