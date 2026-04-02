package ru.suo.integration.eventing;

/**
 * Транспортный адаптер событий (Kafka/DataBus и др.).
 */
public interface EventTransportAdapter {
    void publish(IntegrationEvent event);
}
