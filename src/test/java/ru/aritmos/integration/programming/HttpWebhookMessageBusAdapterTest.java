package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class HttpWebhookMessageBusAdapterTest {

    @Test
    void shouldPublishMessageToWebhookEndpoint() throws IOException {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        AtomicReference<String> capturedAuthorization = new AtomicReference<>("");
        AtomicReference<String> capturedTrace = new AtomicReference<>("");
        AtomicReference<String> capturedContentType = new AtomicReference<>("");
        AtomicReference<String> capturedMethod = new AtomicReference<>("");
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/webhook", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes()));
            capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedTrace.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            capturedMethod.set(exchange.getRequestMethod());
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
            broker.setProperties(Map.of(
                    "url", url,
                    "method", "put",
                    "header.Authorization", "Bearer demo-token",
                    "header.X-Trace-Id", "trace-123",
                    "header.content-type", "text/plain"
            ));

            HttpWebhookMessageBusAdapter adapter = new HttpWebhookMessageBusAdapter(new ObjectMapper());
            Map<String, Object> result = adapter.publish(
                    broker,
                    new BrokerMessageRequest("customer.events", "BR-1", Map.of("branchId", "BR-1"), Map.of("x-test", "1"))
            );

            Assertions.assertEquals("DELIVERED", result.get("status"));
            Assertions.assertTrue(capturedBody.get().contains("\"topic\":\"customer.events\""));
            Assertions.assertEquals("Bearer demo-token", capturedAuthorization.get());
            Assertions.assertEquals("trace-123", capturedTrace.get());
            Assertions.assertEquals("application/json", capturedContentType.get());
            Assertions.assertEquals("PUT", capturedMethod.get());
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

    @Test
    void shouldRejectUnsupportedHttpMethod() {
        IntegrationGatewayConfiguration.MessageBrokerSettings broker = new IntegrationGatewayConfiguration.MessageBrokerSettings();
        broker.setId("webhook-main");
        broker.setType("WEBHOOK_HTTP");
        broker.setEnabled(true);
        broker.setProperties(Map.of(
                "url", "http://127.0.0.1:8080/webhook",
                "method", "DELETE"
        ));
        HttpWebhookMessageBusAdapter adapter = new HttpWebhookMessageBusAdapter(new ObjectMapper());
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> adapter.publish(broker, new BrokerMessageRequest("t", "k", Map.of(), Map.of())));
    }

    @Test
    void shouldValidateTimeoutRangeViaAdapterValidation() {
        HttpWebhookMessageBusAdapter adapter = new HttpWebhookMessageBusAdapter(new ObjectMapper());
        List<String> violations = adapter.validateProperties(Map.of(
                "url", "https://gateway.local/events",
                "timeoutSeconds", "999"
        ));
        Assertions.assertEquals(List.of("timeoutSeconds должен быть в диапазоне 1..120"), violations);
    }
}
