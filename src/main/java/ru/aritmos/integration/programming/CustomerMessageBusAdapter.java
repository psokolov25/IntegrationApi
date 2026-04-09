package ru.aritmos.integration.programming;

import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.util.Map;

/**
 * Расширяемый адаптер взаимодействия с брокером сообщений/шиной данных заказчика.
 */
public interface CustomerMessageBusAdapter {

    boolean supports(String brokerType);

    Map<String, Object> publish(IntegrationGatewayConfiguration.MessageBrokerSettings broker,
                                BrokerMessageRequest message);
}
