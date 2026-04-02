package ru.suo.integration.eventing;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Пример доменного обработчика события visit-created.
 */
@Singleton
public class DefaultVisitCreatedEventHandler implements EventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultVisitCreatedEventHandler.class);

    @Override
    public boolean supports(String eventType) {
        return "visit-created".equalsIgnoreCase(eventType);
    }

    @Override
    public void handle(IntegrationEvent event) {
        LOG.info("EVENT_HANDLED type={} id={} source={}", event.eventType(), event.eventId(), event.source());
    }
}
