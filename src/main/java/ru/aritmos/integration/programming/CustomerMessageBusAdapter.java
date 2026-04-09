package ru.aritmos.integration.programming;

import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.util.List;
import java.util.Map;

/**
 * Расширяемый адаптер взаимодействия с брокером сообщений/шиной данных заказчика.
 */
public interface CustomerMessageBusAdapter {

    boolean supports(String brokerType);

    /**
     * Список явно поддерживаемых типов брокера (для UI/каталога).
     */
    default List<String> supportedBrokerTypes() {
        return List.of();
    }

    Map<String, Object> publish(IntegrationGatewayConfiguration.MessageBrokerSettings broker,
                                BrokerMessageRequest message);
}
