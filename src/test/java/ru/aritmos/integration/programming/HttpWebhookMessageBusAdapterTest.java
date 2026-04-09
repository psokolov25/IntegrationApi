package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class HttpWebhookMessageBusAdapterTest {

    @Test
    void shouldPublishMessageToWebhookEndpoint() throws IOException {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/webhook", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] response = "{\"ok\":true}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
            IntegrationGatewayConfiguration.MessageBrokerSettings broker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
            broker.setId("webhook-main");
            broker.setType("WEBHOOK_HTTP");
            broker.setEnabled(true);
            broker.setProperties(Map.of("url", url));

            HttpWebhookMessageBusAdapter adapter = new HttpWebhookMessageBusAdapter(new ObjectMapper());
            Map<String, Object> result = adapter.publish(
                    broker,
                    new BrokerMessageRequest("customer.events", "BR-1", Map.of("branchId", "BR-1"), Map.of("x-test", "1"))
            );

            Assertions.assertEquals("DELIVERED", result.get("status"));
            Assertions.assertTrue(capturedBody.get().contains("\"topic\":\"customer.events\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectWebhookBrokerWithoutUrl() {
        IntegrationGatewayConfiguration.MessageBrokerSettings broker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        broker.setId("webhook-main");
        broker.setType("WEBHOOK_HTTP");
        broker.setEnabled(true);
        broker.setProperties(Map.of());
        HttpWebhookMessageBusAdapter adapter = new HttpWebhookMessageBusAdapter(new ObjectMapper());
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> adapter.publish(broker, new BrokerMessageRequest("t", "k", Map.of(), Map.of())));
    }
}
