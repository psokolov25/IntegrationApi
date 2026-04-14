package ru.aritmos.integration.security;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.filter.ServerFilterChain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.security.core.AuthenticationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class ApiKeyFilterTest {

    @Test
    void shouldAllowAnonymousModeWithoutCallingAuthenticationService() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getAnonymousAccess().setEnabled(true);
        configuration.getAnonymousAccess().setSubjectId("anonymous-ui");
        configuration.getAnonymousAccess().setPermissions(Set.of("event-process", "programmable-script-execute"));
        AtomicInteger authCalls = new AtomicInteger();
        AuthenticationService authService = request -> {
            authCalls.incrementAndGet();
            return Optional.empty();
        };
        ApiKeyFilter filter = new ApiKeyFilter(authService, configuration);
        AtomicBoolean proceedCalled = new AtomicBoolean(false);
        ServerFilterChain chain = request -> {
            proceedCalled.set(true);
            return Publishers.just(HttpResponse.ok());
        };

        HttpRequest<?> request = HttpRequest.GET("/api/v2/events/stats");
        filter.doFilter(request, chain);

        Assertions.assertTrue(proceedCalled.get());
        Assertions.assertEquals(0, authCalls.get());
        SubjectPrincipal subject = RequestSecurityContext.current(request).orElseThrow();
        Assertions.assertEquals("anonymous-ui", subject.subjectId());
        Assertions.assertTrue(subject.permissions().contains("event-process"));
    }

    @Test
    void shouldRequireAuthenticationWhenAnonymousModeDisabled() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getAnonymousAccess().setEnabled(false);
        AtomicBoolean authCalled = new AtomicBoolean(false);
        AuthenticationService authService = request -> {
            authCalled.set(true);
            return Optional.empty();
        };
        ApiKeyFilter filter = new ApiKeyFilter(authService, configuration);
        AtomicBoolean proceedCalled = new AtomicBoolean(false);
        ServerFilterChain chain = request -> {
            proceedCalled.set(true);
            return Publishers.just(HttpResponse.ok());
        };

        HttpRequest<?> request = HttpRequest.GET("/api/v2/events/stats");
        filter.doFilter(request, chain);

        Assertions.assertTrue(authCalled.get());
        Assertions.assertFalse(proceedCalled.get());
        Assertions.assertTrue(RequestSecurityContext.current(request).isEmpty());
    }
}
