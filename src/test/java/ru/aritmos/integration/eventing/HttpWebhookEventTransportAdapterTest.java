package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class HttpWebhookEventTransportAdapterTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSkipWhenWebhookDisabled() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().getWebhook().setEnabled(false);
        HttpWebhookEventTransportAdapter adapter = new HttpWebhookEventTransportAdapter(
                configuration,
                new ObjectMapper(),
                new DefaultExternalSystemAudienceResolver()
        );

        adapter.publish(sampleEvent());
    }

    @Test
    void shouldPostEventToWebhookWhenEnabled() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<String> authRef = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ingest", exchange -> {
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes()));
            authRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "ok");
        });
        server.start();

        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().getWebhook().setEnabled(true);
        configuration.getEventing().getWebhook().setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/ingest");
        configuration.getEventing().getWebhook().setAuthToken("Bearer test-token");
        HttpWebhookEventTransportAdapter adapter = new HttpWebhookEventTransportAdapter(
                configuration,
                new ObjectMapper(),
                new DefaultExternalSystemAudienceResolver()
        );

        adapter.publish(sampleEvent());

        Assertions.assertNotNull(bodyRef.get());
        Assertions.assertTrue(bodyRef.get().contains("\"eventId\":\"evt-1\""));
        Assertions.assertEquals("Bearer test-token", authRef.get());
    }

    @Test
    void shouldFailOnNon2xxWebhookResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ingest", exchange -> respond(exchange, 503, "unavailable"));
        server.start();

        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().getWebhook().setEnabled(true);
        configuration.getEventing().getWebhook().setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/ingest");
        HttpWebhookEventTransportAdapter adapter = new HttpWebhookEventTransportAdapter(
                configuration,
                new ObjectMapper(),
                new DefaultExternalSystemAudienceResolver()
        );

        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class, () -> adapter.publish(sampleEvent()));
        Assertions.assertTrue(ex.getMessage().contains("status=503"));
    }

    @Test
    void shouldSkipWebhookWhenTargetSystemsDoNotMatch() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ingest", exchange -> {
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, "ok");
        });
        server.start();

        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().getWebhook().setEnabled(true);
        configuration.getEventing().getWebhook().setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/ingest");
        configuration.getEventing().getWebhook().setTargetSystems(List.of("non-existent-system"));
        HttpWebhookEventTransportAdapter adapter = new HttpWebhookEventTransportAdapter(
                configuration,
                new ObjectMapper(),
                new DefaultExternalSystemAudienceResolver()
        );

        adapter.publish(sampleEvent());

        Assertions.assertNull(bodyRef.get());
    }

    private IntegrationEvent sampleEvent() {
        return new IntegrationEvent(
                "evt-1",
                "visit-created",
                "databus",
                null,
                Map.of("branchId", "BR-01")
        );
    }

    private static void respond(HttpExchange exchange, int code, String payload) throws IOException {
        byte[] body = payload.getBytes();
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
        exchange.close();
    }
}
