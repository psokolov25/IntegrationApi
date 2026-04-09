package ru.aritmos.integration.security;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.security.core.AuthenticationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;

import java.util.Optional;

/**
 * Фильтр базовой/внутренней аутентификации.
 */
@Filter("/**")
public class ApiKeyFilter implements HttpServerFilter {

    private final AuthenticationService authenticationService;
    private final IntegrationGatewayConfiguration configuration;

    public ApiKeyFilter(AuthenticationService authenticationService,
                        IntegrationGatewayConfiguration configuration) {
        this.authenticationService = authenticationService;
        this.configuration = configuration;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        String path = request.getPath();
        if (path.startsWith("/health") || path.startsWith("/swagger") || path.startsWith("/swagger-ui") || path.startsWith("/openapi") || path.startsWith("/ui") || path.startsWith("/api/v1/auth/token")) {
            return chain.proceed(request);
        }
        if (configuration.getAnonymousAccess().isEnabled()) {
            RequestSecurityContext.attach(request, new SubjectPrincipal(
                    configuration.getAnonymousAccess().getSubjectId(),
                    configuration.getAnonymousAccess().getPermissions()
            ));
            return chain.proceed(request);
        }

        Optional<SubjectPrincipal> subject = authenticationService.authenticate(request);
        if (subject.isEmpty()) {
            return Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
        }
        RequestSecurityContext.attach(request, subject.get());
        return chain.proceed(request);
    }
}
