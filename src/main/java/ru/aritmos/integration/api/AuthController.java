package ru.aritmos.integration.api;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.aritmos.integration.domain.InternalTokenRequest;
import ru.aritmos.integration.domain.InternalTokenResponse;
import ru.aritmos.integration.security.internal.InternalAuthApplicationService;

/**
 * Internal auth endpoint для technical clients.
 */
@Controller("/api/v1/auth")
@Tag(name = "Internal Auth", description = "Внутренняя аутентификация технических клиентов")
public class AuthController {

    private final InternalAuthApplicationService authService;

    public AuthController(InternalAuthApplicationService authService) {
        this.authService = authService;
    }

    @Post("/token")
    @Operation(summary = "Получить internal токен", description = "Выдает токен для technical client в internal режиме безопасности.")
    public InternalTokenResponse token(@Valid @Body InternalTokenRequest request) {
        String token = authService.issueToken(request.clientId(), request.clientSecret());
        return new InternalTokenResponse(token, "Bearer", 3600);
    }
}
