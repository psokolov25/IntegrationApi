package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

class ProgrammableHttpExchangeProcessorTest {

    @Test
    void shouldAddDirectionHeaderAndEnvelopeWhenEnabled() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getProgrammableApi().getHttpProcessing().setEnabled(true);
        configuration.getProgrammableApi().getHttpProcessing().setRequestEnvelopeEnabled(true);
        ProgrammableHttpExchangeProcessor processor = new ProgrammableHttpExchangeProcessor(configuration, new ObjectMapper());

        Map<String, String> headers = processor.enrichHeaders(Map.of("X-Req", "1"), "OUTBOUND_EXTERNAL");
        Map<String, Object> body = processor.enrichBody(Map.of("value", 7), "OUTBOUND_EXTERNAL");

        Assertions.assertEquals("OUTBOUND_EXTERNAL", headers.get("X-Integration-Direction"));
        Assertions.assertEquals("1", headers.get("X-Req"));
        Assertions.assertTrue(body.containsKey("meta"));
        Assertions.assertTrue(body.containsKey("data"));
    }

    @Test
    void shouldReturnRawBodyWhenProcessingDisabled() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getProgrammableApi().getHttpProcessing().setEnabled(false);
        ProgrammableHttpExchangeProcessor processor = new ProgrammableHttpExchangeProcessor(configuration, new ObjectMapper());

        Map<String, String> headers = processor.enrichHeaders(Map.of("A", "B"), "INBOUND_SUO");
        Map<String, Object> body = processor.enrichBody(Map.of("x", "y"), "INBOUND_SUO");

        Assertions.assertFalse(headers.containsKey("X-Integration-Direction"));
        Assertions.assertEquals(Map.of("x", "y"), body);
    }

    @Test
    void shouldProcessResponseWithPreviewAndJson() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getProgrammableApi().getHttpProcessing().setResponseBodyMaxChars(5);
        configuration.getProgrammableApi().getHttpProcessing().setParseJsonBody(true);
        ProgrammableHttpExchangeProcessor processor = new ProgrammableHttpExchangeProcessor(configuration, new ObjectMapper());

        HttpResponse<String> response = new StubHttpResponse(200, "{\"ok\":true,\"value\":42}");
        Map<String, Object> processed = processor.processResponse(response);

        Assertions.assertEquals(200, processed.get("status"));
        Assertions.assertEquals("{\"ok\"...", processed.get("bodyPreview"));
        Assertions.assertNotNull(processed.get("json"));
    }

    @Test
    void shouldProcessRawResponseWithoutHttpClientResponseWrapper() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getProgrammableApi().getHttpProcessing().setResponseBodyMaxChars(4);
        ProgrammableHttpExchangeProcessor processor = new ProgrammableHttpExchangeProcessor(configuration, new ObjectMapper());

        Map<String, Object> processed = processor.processRawResponse(
                201,
                "{\"k\":1}",
                Map.of("Content-Type", java.util.List.of("application/json"))
        );

        Assertions.assertEquals(201, processed.get("status"));
        Assertions.assertEquals("{\"k\"...", processed.get("bodyPreview"));
        Assertions.assertNotNull(processed.get("json"));
    }

    @Test
    void shouldNormalizeUnknownDirectionToOutbound() {
        ProgrammableHttpExchangeProcessor processor = new ProgrammableHttpExchangeProcessor(
                new IntegrationGatewayConfiguration(),
                new ObjectMapper()
        );
        Assertions.assertEquals("OUTBOUND_EXTERNAL", processor.normalizeDirection("unknown"));
        Assertions.assertEquals(java.util.List.of("OUTBOUND_EXTERNAL", "INBOUND_SUO"), processor.supportedDirections());
    }

    private record StubHttpResponse(int statusCode, String body) implements HttpResponse<String> {
        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder().uri(URI.create("http://localhost")).build();
        }

        @Override
        public java.util.Optional<HttpResponse<String>> previousResponse() {
            return java.util.Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("X-Test", java.util.List.of("1")), (a, b) -> true);
        }

        @Override
        public URI uri() {
            return URI.create("http://localhost");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
            return java.util.Optional.empty();
        }

    }
}
