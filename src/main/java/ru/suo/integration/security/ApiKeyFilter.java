package ru.suo.integration.security;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;
import ru.suo.integration.security.core.AuthenticationService;
import ru.suo.integration.security.core.SubjectPrincipal;

import java.util.Optional;

/**
 * Фильтр базовой/внутренней аутентификации.
 */
@Filter("/**")
public class ApiKeyFilter implements HttpServerFilter {

    private final AuthenticationService authenticationService;

    public ApiKeyFilter(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        String path = request.getPath();
        if (path.startsWith("/health") || path.startsWith("/swagger") || path.startsWith("/openapi") || path.startsWith("/api/v1/auth/token")) {
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
