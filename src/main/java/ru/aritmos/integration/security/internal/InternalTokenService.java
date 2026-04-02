package ru.aritmos.integration.security.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.security.core.SubjectPrincipal;
import ru.aritmos.integration.security.core.TokenService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Простейший HMAC-токен для internal auth режима.
 */
@Singleton
public class InternalTokenService implements TokenService {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntegrationGatewayConfiguration configuration;

    public InternalTokenService(IntegrationGatewayConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String issueToken(String subjectId, String secret, Set<String> permissions) {
        try {
            Map<String, Object> payload = Map.of(
                    "sub", subjectId,
                    "perm", permissions,
                    "exp", Instant.now().plusSeconds(3600).getEpochSecond()
            );
            String body = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(payload));
            String signature = sign(body, secret);
            return body + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось выпустить токен", ex);
        }
    }

    @Override
    public Optional<SubjectPrincipal> parseToken(String token) {
        try {
            String[] chunks = token.split("\\.");
            if (chunks.length != 2) {
                return Optional.empty();
            }

            String expectedSign = sign(chunks[0], configuration.getInternalSigningKey());
            if (!expectedSign.equals(chunks[1])) {
                return Optional.empty();
            }

            byte[] decoded = Base64.getUrlDecoder().decode(chunks[0]);
            Map<String, Object> payload = objectMapper.readValue(decoded, new TypeReference<>() {});
            Number exp = (Number) payload.get("exp");
            if (exp == null || Instant.now().isAfter(Instant.ofEpochSecond(exp.longValue()))) {
                return Optional.empty();
            }
            String sub = String.valueOf(payload.get("sub"));
            @SuppressWarnings("unchecked")
            Set<String> permissions = Set.copyOf((java.util.List<String>) payload.get("perm"));
            return Optional.of(new SubjectPrincipal(sub, permissions));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось подписать токен", ex);
        }
    }
}
