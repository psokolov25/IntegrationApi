package ru.suo.integration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.suo.integration.config.IntegrationGatewayConfiguration;
import ru.suo.integration.security.keycloak.KeycloakJwtAuthenticationService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

class KeycloakJwtAuthenticationServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldAuthenticateValidJwt() throws Exception {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getKeycloak().setIssuer("https://issuer");
        cfg.getKeycloak().setAudience("integration-api");
        cfg.getKeycloak().setSharedSecret("kc-secret");

        KeycloakJwtAuthenticationService service = new KeycloakJwtAuthenticationService(cfg);
        String token = token("kc-secret", "https://issuer", "integration-api", "subject-a");

        var principal = service.authenticate(token);

        Assertions.assertTrue(principal.isPresent());
        Assertions.assertEquals("subject-a", principal.get().subjectId());
        Assertions.assertTrue(principal.get().permissions().contains("queue-view"));
    }

    @Test
    void shouldRejectWrongIssuer() throws Exception {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getKeycloak().setIssuer("https://issuer-ok");
        cfg.getKeycloak().setAudience("integration-api");
        cfg.getKeycloak().setSharedSecret("kc-secret");

        KeycloakJwtAuthenticationService service = new KeycloakJwtAuthenticationService(cfg);
        String token = token("kc-secret", "https://issuer-bad", "integration-api", "subject-a");

        Assertions.assertTrue(service.authenticate(token).isEmpty());
    }

    private String token(String secret, String issuer, String audience, String sub) throws Exception {
        String header = b64(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
        String payload = b64(mapper.writeValueAsBytes(Map.of(
                "iss", issuer,
                "aud", audience,
                "sub", sub,
                "exp", Instant.now().plusSeconds(300).getEpochSecond(),
                "realm_access", Map.of("roles", java.util.List.of("queue-view", "metrics-view"))
        )));
        String signature = sign(header + "." + payload, secret);
        return header + "." + payload + "." + signature;
    }

    private String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sign(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
