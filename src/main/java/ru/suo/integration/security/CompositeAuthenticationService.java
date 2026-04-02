package ru.suo.integration.security;

import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import ru.suo.integration.config.IntegrationGatewayConfiguration;
import ru.suo.integration.security.core.AuthenticationService;
import ru.suo.integration.security.core.SecurityMode;
import ru.suo.integration.security.core.SubjectPrincipal;
import ru.suo.integration.security.core.TokenService;
import ru.suo.integration.security.keycloak.KeycloakJwtAuthenticationService;

import java.util.Optional;
import java.util.Set;

/**
 * Переключаемая аутентификация: API key, internal bearer token, keycloak/hybrid.
 */
@Singleton
public class CompositeAuthenticationService implements AuthenticationService {

    private static final Set<String> API_KEY_PERMISSIONS = Set.of(
            "queue-view", "queue-call", "queue-aggregate", "metrics-view", "event-process"
    );

    private final IntegrationGatewayConfiguration configuration;
    private final ApiKeyAuthService apiKeyAuthService;
    private final TokenService tokenService;
    private final KeycloakJwtAuthenticationService keycloakAuthService;
    private final LocalPermissionEnricher permissionEnricher;

    public CompositeAuthenticationService(IntegrationGatewayConfiguration configuration,
                                          ApiKeyAuthService apiKeyAuthService,
                                          TokenService tokenService,
                                          KeycloakJwtAuthenticationService keycloakAuthService,
                                          LocalPermissionEnricher permissionEnricher) {
        this.configuration = configuration;
        this.apiKeyAuthService = apiKeyAuthService;
        this.tokenService = tokenService;
        this.keycloakAuthService = keycloakAuthService;
        this.permissionEnricher = permissionEnricher;
    }

    @Override
    public Optional<SubjectPrincipal> authenticate(HttpRequest<?> request) {
        SecurityMode mode = configuration.getSecurityMode();
        return switch (mode) {
            case INTERNAL -> authenticateInternal(request);
            case KEYCLOAK -> authenticateKeycloak(request, false);
            case HYBRID -> authenticateKeycloak(request, true);
            case API_KEY -> authenticateApiKey(request);
        };
    }

    private Optional<SubjectPrincipal> authenticateInternal(HttpRequest<?> request) {
        String auth = request.getHeaders().getAuthorization().orElse("");
        if (!auth.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return tokenService.parseToken(auth.substring("Bearer ".length()));
    }

    private Optional<SubjectPrincipal> authenticateKeycloak(HttpRequest<?> request, boolean enrichLocalPermissions) {
        String auth = request.getHeaders().getAuthorization().orElse("");
        if (!auth.startsWith("Bearer ")) {
            return Optional.empty();
        }
        Optional<SubjectPrincipal> principal = keycloakAuthService.authenticate(auth.substring("Bearer ".length()));
        if (principal.isEmpty() || !enrichLocalPermissions) {
            return principal;
        }
        return principal.map(permissionEnricher::enrich);
    }

    private Optional<SubjectPrincipal> authenticateApiKey(HttpRequest<?> request) {
        String apiKey = request.getHeaders().get("X-Api-Key");
        if (!apiKeyAuthService.isAllowed(apiKey)) {
            return Optional.empty();
        }
        return Optional.of(new SubjectPrincipal("apikey-client", API_KEY_PERMISSIONS));
    }
}
