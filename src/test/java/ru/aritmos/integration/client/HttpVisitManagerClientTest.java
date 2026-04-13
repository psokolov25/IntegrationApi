package ru.aritmos.integration.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.domain.BranchStateUpdateRequest;
import ru.aritmos.integration.domain.CallVisitorRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

class HttpVisitManagerClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldCallRealHttpEndpointsForStage1Operations() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/queues/BR-701", exchange -> writeJson(exchange, 200, """
                [{"queueId":"Q-1","queueName":"Основная","waitingCount":5}]
                """));
        server.createContext("/api/v1/queues/BR-701/call/VIS-1", exchange -> writeJson(exchange, 200, """
                {"visitId":"VIS-1","status":"CALLED","sourceVisitManagerId":"vm-main"}
                """));
        server.createContext("/api/v1/branches/BR-701/state", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 200, """
                        {"branchId":"BR-701","sourceVisitManagerId":"vm-main","status":"OPEN","activeWindow":"08:00-20:00","queueSize":3,"updatedAt":"2026-04-13T10:00:00Z","cached":false,"updatedBy":"vm-sync"}
                        """);
                return;
            }
            writeJson(exchange, 200, """
                    {"branchId":"BR-701","sourceVisitManagerId":"vm-main","status":"PAUSED","activeWindow":"08:00-20:00","queueSize":4,"updatedAt":"2026-04-13T10:05:00Z","cached":false,"updatedBy":"operator"}
                    """);
        });
        server.start();

        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        cfg.setVisitManagers(List.of(vm));
        cfg.getVisitManagerClient().setMode("HTTP");

        HttpVisitManagerClient client = new HttpVisitManagerClient(cfg, new ObjectMapper());

        var queues = client.getQueues("vm-main", "BR-701");
        Assertions.assertEquals(1, queues.size());
        Assertions.assertEquals("Q-1", queues.get(0).queueId());

        var call = client.callVisitor("vm-main", "VIS-1",
                new CallVisitorRequest("BR-701", "Q-1", "op-1", "idem-1"));
        Assertions.assertEquals("CALLED", call.status());

        BranchStateDto state = client.getBranchState("vm-main", "BR-701");
        Assertions.assertEquals("OPEN", state.status());
        Assertions.assertEquals(Instant.parse("2026-04-13T10:00:00Z"), state.updatedAt());

        BranchStateDto updated = client.updateBranchState("vm-main", "BR-701",
                new BranchStateUpdateRequest("PAUSED", "08:00-20:00", 4, "operator"));
        Assertions.assertEquals("PAUSED", updated.status());
        Assertions.assertEquals("operator", updated.updatedBy());
    }

    @Test
    void shouldFailWhenBranchStateResponseOmitsRequiredCanonicalFields() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/branches/BR-702/state", exchange -> writeJson(exchange, 200, """
                {"branchId":"BR-702","status":"OPEN","activeWindow":"08:00-20:00","queueSize":0,"cached":false,"updatedBy":"vm-sync"}
                """));
        server.start();

        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-alt");
        vm.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        cfg.setVisitManagers(List.of(vm));
        cfg.getVisitManagerClient().setMode("HTTP");

        HttpVisitManagerClient client = new HttpVisitManagerClient(cfg, new ObjectMapper());
        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
                () -> client.getBranchState("vm-alt", "BR-702"));
        Assertions.assertTrue(ex.getMessage().contains("Некорректный ответ VisitManager branch-state API"));
        Assertions.assertNotNull(ex.getCause());
        Assertions.assertTrue(ex.getCause().getMessage().contains("sourceVisitManagerId")
                        || ex.getCause().getMessage().contains("updatedAt"),
                "Причина ошибки должна указывать на отсутствие обязательных канонических полей");
    }

    @Test
    void shouldApplyCustomPathTemplatesAndAuthHeader() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicBoolean authHeaderSeen = new AtomicBoolean(false);
        server.createContext("/vm/queue/BR-703", exchange -> {
            authHeaderSeen.set("Bearer secret-token".equals(exchange.getRequestHeaders().getFirst("Authorization")));
            writeJson(exchange, 200, """
                    [{"queueId":"Q-703","queueName":"Кастом","waitingCount":1}]
                    """);
        });
        server.start();

        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        cfg.setVisitManagers(List.of(vm));
        cfg.getVisitManagerClient().setMode("HTTP");
        cfg.getVisitManagerClient().setQueuesPathTemplate("/vm/queue/{branchId}");
        cfg.getVisitManagerClient().setAuthHeader("Authorization");
        cfg.getVisitManagerClient().setAuthToken("Bearer secret-token");

        HttpVisitManagerClient client = new HttpVisitManagerClient(cfg, new ObjectMapper());
        var queues = client.getQueues("vm-main", "BR-703");

        Assertions.assertEquals(1, queues.size());
        Assertions.assertEquals("Q-703", queues.get(0).queueId());
        Assertions.assertTrue(authHeaderSeen.get(), "downstream auth header должен быть передан");
    }

    @Test
    void shouldReadBranchStateUsingRuntimeResponseMappingPaths() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/branches/BR-800/state", exchange -> writeJson(exchange, 200, """
                {
                  "state": {
                    "branch": {"code":"BR-800"},
                    "meta": {"vm":"vm-main"},
                    "statusInfo": {"value":"OPEN"},
                    "window": "08:00-20:00",
                    "queue": {"size": 6},
                    "updated": {"at":"2026-04-13T12:45:00Z","by":"vm-sync"}
                  }
                }
                """));
        server.start();

        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        cfg.setVisitManagers(List.of(vm));
        cfg.getVisitManagerClient().setMode("HTTP");
        cfg.getVisitManagerClient().getBranchStateResponseMapping().setBranchIdPath("state.branch.code");
        cfg.getVisitManagerClient().getBranchStateResponseMapping().setSourceVisitManagerIdPath("state.meta.vm");
        cfg.getVisitManagerClient().getBranchStateResponseMapping().setStatusPath("state.statusInfo.value");
        cfg.getVisitManagerClient().getBranchStateResponseMapping().setActiveWindowPath("state.window");
        cfg.getVisitManagerClient().getBranchStateResponseMapping().setQueueSizePath("state.queue.size");
        cfg.getVisitManagerClient().getBranchStateResponseMapping().setUpdatedAtPath("state.updated.at");
        cfg.getVisitManagerClient().getBranchStateResponseMapping().setUpdatedByPath("state.updated.by");

        HttpVisitManagerClient client = new HttpVisitManagerClient(cfg, new ObjectMapper());
        BranchStateDto state = client.getBranchState("vm-main", "BR-800");

        Assertions.assertEquals("BR-800", state.branchId());
        Assertions.assertEquals("vm-main", state.sourceVisitManagerId());
        Assertions.assertEquals("OPEN", state.status());
        Assertions.assertEquals("08:00-20:00", state.activeWindow());
        Assertions.assertEquals(6, state.queueSize());
        Assertions.assertEquals(Instant.parse("2026-04-13T12:45:00Z"), state.updatedAt());
        Assertions.assertEquals("vm-sync", state.updatedBy());
    }

    @Test
    void shouldFailWithClearMessageWhenVisitManagerBaseUrlMissing() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl(" ");
        cfg.setVisitManagers(List.of(vm));
        cfg.getVisitManagerClient().setMode("HTTP");

        HttpVisitManagerClient client = new HttpVisitManagerClient(cfg, new ObjectMapper());

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> client.getQueues("vm-main", "BR-900"));
        Assertions.assertTrue(ex.getMessage().contains("Не задан base-url"),
                "Ошибка должна явно указывать на отсутствие base-url");
    }

    private void writeJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes();
        exchange.getResponseHeaders().put("Content-Type", List.of("application/json"));
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
