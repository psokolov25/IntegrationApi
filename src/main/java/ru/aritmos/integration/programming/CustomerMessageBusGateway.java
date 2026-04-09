package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.util.List;
import java.util.Map;

/**
 * Унифицированный gateway отправки сообщений в брокеры/шины данных заказчика.
 */
@Singleton
public class CustomerMessageBusGateway {

    private final IntegrationGatewayConfiguration configuration;
    private final List<CustomerMessageBusAdapter> adapters;

    public CustomerMessageBusGateway(IntegrationGatewayConfiguration configuration,
                                     List<CustomerMessageBusAdapter> adapters) {
        this.configuration = configuration;
        this.adapters = adapters;
    }

    public Map<String, Object> publish(String brokerId,
                                       String topic,
                                       String key,
                                       Map<String, Object> payload,
                                       Map<String, String> headers) {
        IntegrationGatewayConfiguration.MessageBrokerSettings broker = configuration.getProgrammableApi()
                .getMessageBrokers()
                .stream()
                .filter(item -> item.getId().equals(brokerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Брокер не найден: " + brokerId));
        if (!broker.isEnabled()) {
            throw new IllegalStateException("Брокер отключен: " + brokerId);
        }
        CustomerMessageBusAdapter adapter = adapters.stream()
                .filter(item -> item.supports(broker.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет adapter для типа брокера: " + broker.getType()));

        return adapter.publish(broker, new BrokerMessageRequest(topic, key, payload, headers));
    }
}
