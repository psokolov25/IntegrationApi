package ru.suo.integration.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.suo.integration.config.IntegrationGatewayConfiguration;
import ru.suo.integration.security.internal.InternalTokenService;

import java.util.Set;

class InternalTokenServiceTest {

    @Test
    void shouldIssueAndParseToken() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.setInternalSigningKey("sign-key");
        InternalTokenService service = new InternalTokenService(configuration);

        String token = service.issueToken("client-a", "sign-key", Set.of("queue-view"));
        var parsed = service.parseToken(token);

        Assertions.assertTrue(parsed.isPresent());
        Assertions.assertEquals("client-a", parsed.get().subjectId());
        Assertions.assertTrue(parsed.get().permissions().contains("queue-view"));
    }

    @Test
    void shouldRejectTokenWithWrongSignature() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.setInternalSigningKey("sign-key");
        InternalTokenService service = new InternalTokenService(configuration);

        String token = service.issueToken("client-a", "another-key", Set.of("queue-view"));
        Assertions.assertTrue(service.parseToken(token).isEmpty());
    }
}
