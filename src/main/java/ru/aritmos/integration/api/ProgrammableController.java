package ru.aritmos.integration.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integration.programming.ProgrammableEndpointService;
import ru.aritmos.integration.security.RequestSecurityContext;

/**
 * Контроллер programmable endpoints (декларативная модель).
 */
@Controller("/api/v1/program")
@Tag(name = "Programmable API", description = "Декларативные кастомные endpoints для интеграций")
public class ProgrammableController {

    private final ProgrammableEndpointService programmableEndpointService;

    public ProgrammableController(ProgrammableEndpointService programmableEndpointService) {
        this.programmableEndpointService = programmableEndpointService;
    }

    @Post("/{endpointId}")
    @Operation(summary = "Вызов programmable endpoint", description = "Исполняет заранее зарегистрированную безопасную операцию без произвольного eval.")
    public Object execute(HttpRequest<?> request, @PathVariable String endpointId, @Body JsonNode payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        return programmableEndpointService.execute(endpointId, subject, payload);
    }
}
