package ru.aritmos.integration.eventing;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

/**
 * Базовый Kafka/DataBus transport adapter (логирующий placeholder для этапа 6.2).
 */
@Singleton
public class KafkaDataBusTransportAdapter implements EventTransportAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaDataBusTransportAdapter.class);
    private final IntegrationGatewayConfiguration configuration;
    private final ExternalSystemAudienceResolver audienceResolver;

    public KafkaDataBusTransportAdapter(IntegrationGatewayConfiguration configuration) {
        this(configuration, new DefaultExternalSystemAudienceResolver());
    }

    KafkaDataBusTransportAdapter(IntegrationGatewayConfiguration configuration,
                                 ExternalSystemAudienceResolver audienceResolver) {
        this.configuration = configuration;
        this.audienceResolver = audienceResolver;
    }

    @Override
    public void publish(IntegrationEvent event) {
        if (!configuration.getEventing().getKafka().isEnabled()) {
            return;
        }
        LOG.info("EVENT_MEDIATED topic={} eventId={} type={} source={} targets={}",
                configuration.getEventing().getKafka().getOutboundTopic(),
                event.eventId(),
                event.eventType(),
                event.source(),
                String.join(",", audienceResolver.resolve(event)));
    }
}
