package ru.aritmos.integration.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integration.domain.GroovyScriptInfoDto;
import ru.aritmos.integration.domain.GroovyScriptUpsertRequest;
import ru.aritmos.integration.domain.InboundMessageReactionRequest;
import ru.aritmos.integration.programming.GroovyScriptService;
import ru.aritmos.integration.programming.ProgrammableEndpointService;
import ru.aritmos.integration.security.RequestSecurityContext;

import java.util.Map;
import java.util.List;

/**
 * Контроллер programmable endpoints (декларативная модель).
 */
@Controller("/api/v1/program")
@Tag(name = "Programmable API", description = "Декларативные кастомные endpoints для интеграций")
public class ProgrammableController {

    private final ProgrammableEndpointService programmableEndpointService;
    private final GroovyScriptService groovyScriptService;

    public ProgrammableController(ProgrammableEndpointService programmableEndpointService,
                                  GroovyScriptService groovyScriptService) {
        this.programmableEndpointService = programmableEndpointService;
        this.groovyScriptService = groovyScriptService;
    }

    @Post("/{endpointId}")
    @Operation(summary = "Вызов programmable endpoint", description = "Исполняет заранее зарегистрированную безопасную операцию без произвольного eval.")
    public Object execute(HttpRequest<?> request, @PathVariable String endpointId, @Body JsonNode payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        return programmableEndpointService.execute(endpointId, subject, payload);
    }

    @Put("/scripts/{scriptId}")
    @Operation(summary = "Сохранить Groovy-скрипт", description = "Сохраняет/обновляет Groovy-скрипт в Redis (или fallback-хранилище).")
    public GroovyScriptInfoDto saveScript(HttpRequest<?> request,
                                          @PathVariable String scriptId,
                                          @Body GroovyScriptUpsertRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        var script = groovyScriptService.save(scriptId, payload.type(), payload.scriptBody(), payload.description(), subject);
        return new GroovyScriptInfoDto(
                script.scriptId(),
                script.type(),
                script.description(),
                script.scriptBody(),
                script.updatedAt(),
                script.updatedBy()
        );
    }

    @Get("/scripts/{scriptId}")
    @Operation(summary = "Получить Groovy-скрипт", description = "Возвращает сохраненный Groovy-скрипт по id.")
    public GroovyScriptInfoDto getScript(HttpRequest<?> request, @PathVariable String scriptId) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        var script = groovyScriptService.get(scriptId, subject);
        if (script == null) {
            throw new IllegalArgumentException("Groovy script не найден: " + scriptId);
        }
        return new GroovyScriptInfoDto(
                script.scriptId(),
                script.type(),
                script.description(),
                script.scriptBody(),
                script.updatedAt(),
                script.updatedBy()
        );
    }

    @Delete("/scripts/{scriptId}")
    @Operation(summary = "Удалить Groovy-скрипт", description = "Удаляет Groovy-скрипт из Redis (или fallback-хранилища).")
    public Map<String, Object> deleteScript(HttpRequest<?> request, @PathVariable String scriptId) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        boolean deleted = groovyScriptService.delete(scriptId, subject);
        return Map.of("scriptId", scriptId, "deleted", deleted);
    }

    @Post("/scripts/{scriptId}/execute")
    @Operation(summary = "Выполнить Groovy-скрипт", description = "Выполняет сохраненный Groovy-скрипт для branch-cache запроса или REST-действия в VisitManager.")
    public Object executeScript(HttpRequest<?> request,
                                @PathVariable String scriptId,
                                @Body JsonNode payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        return groovyScriptService.execute(scriptId, payload, subject);
    }

    @Post("/messages/inbound")
    @Operation(summary = "Обработать входящее сообщение шины", description = "Выполняет Groovy-реакции на сообщения брокеров/шин заказчика.")
    public List<Object> reactOnInboundMessage(HttpRequest<?> request,
                                              @Body InboundMessageReactionRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        return groovyScriptService.reactOnIncomingMessage(
                payload.brokerId(),
                payload.topic(),
                payload.key(),
                payload.payload(),
                payload.headers(),
                payload.scriptId(),
                subject
        );
    }
}
