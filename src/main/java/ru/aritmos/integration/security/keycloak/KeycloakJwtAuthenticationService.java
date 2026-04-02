package ru.aritmos.integration.security.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.security.core.SubjectPrincipal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Упрощенная проверка JWT (HS256) для Keycloak integration layer.
 */
@Singleton
public class KeycloakJwtAuthenticationService {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntegrationGatewayConfiguration configuration;

    public KeycloakJwtAuthenticationService(IntegrationGatewayConfiguration configuration) {
        this.configuration = configuration;
    }

    public Optional<SubjectPrincipal> authenticate(String bearerToken) {
        try {
            String[] chunks = bearerToken.split("\\.");
            if (chunks.length != 3) {
                return Optional.empty();
            }

            String expected = sign(chunks[0] + "." + chunks[1], configuration.getKeycloak().getSharedSecret());
            if (!expected.equals(chunks[2])) {
                return Optional.empty();
            }

            Map<String, Object> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(chunks[1]), new TypeReference<>() {});
            if (!configuration.getKeycloak().getIssuer().isBlank()) {
                if (!configuration.getKeycloak().getIssuer().equals(payload.get("iss"))) {
                    return Optional.empty();
                }
            }
            if (!configuration.getKeycloak().getAudience().isBlank()) {
                if (!configuration.getKeycloak().getAudience().equals(payload.get("aud"))) {
                    return Optional.empty();
                }
            }
            Number exp = (Number) payload.get("exp");
            if (exp == null || Instant.now().isAfter(Instant.ofEpochSecond(exp.longValue()))) {
                return Optional.empty();
            }

            String subject = String.valueOf(payload.get("sub"));
            @SuppressWarnings("unchecked")
            Map<String, Object> realmAccess = (Map<String, Object>) payload.getOrDefault("realm_access", Map.of());
            @SuppressWarnings("unchecked")
            Set<String> roles = Set.copyOf((java.util.List<String>) realmAccess.getOrDefault("roles", java.util.List.of()));
            return Optional.of(new SubjectPrincipal(subject, roles));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String sign(String value, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось проверить JWT", ex);
        }
    }
}
