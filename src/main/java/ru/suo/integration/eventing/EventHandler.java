package ru.suo.integration.eventing;

/**
 * Контракт обработчика события.
 */
public interface EventHandler {
    boolean supports(String eventType);
    void handle(IntegrationEvent event);
}
