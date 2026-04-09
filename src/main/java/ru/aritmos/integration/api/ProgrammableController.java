package ru.aritmos.integration.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integration.domain.GroovyScriptInfoDto;
import ru.aritmos.integration.domain.GroovyScriptUpsertRequest;
import ru.aritmos.integration.domain.InboundMessageReactionRequest;
import ru.aritmos.integration.domain.IntegrationTemplateExportRequest;
import ru.aritmos.integration.domain.IntegrationTemplatePreviewDto;
import ru.aritmos.integration.domain.ScriptExecutionRequest;
import ru.aritmos.integration.programming.GroovyScriptService;
import ru.aritmos.integration.programming.IntegrationTemplateArchiveService;
import ru.aritmos.integration.programming.ProgrammableEndpointService;
import ru.aritmos.integration.security.RequestSecurityContext;
import ru.aritmos.integration.security.core.AuthorizationService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Контроллер programmable endpoints (декларативная модель).
 */
@Controller("/api/v1/program")
@Tag(name = "Programmable API", description = "Декларативные кастомные endpoints для интеграций")
public class ProgrammableController {

    private final ProgrammableEndpointService programmableEndpointService;
    private final GroovyScriptService groovyScriptService;
    private final IntegrationTemplateArchiveService templateArchiveService;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;

    public ProgrammableController(ProgrammableEndpointService programmableEndpointService,
                                  GroovyScriptService groovyScriptService,
                                  IntegrationTemplateArchiveService templateArchiveService,
                                  ObjectMapper objectMapper,
                                  AuthorizationService authorizationService) {
        this.programmableEndpointService = programmableEndpointService;
        this.groovyScriptService = groovyScriptService;
        this.templateArchiveService = templateArchiveService;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
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

    @Get("/scripts")
    @Operation(summary = "Список Groovy-скриптов", description = "Возвращает все сохраненные скрипты для панели управления и редактора.")
    public List<GroovyScriptInfoDto> listScripts(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        return groovyScriptService.list(subject).stream()
                .map(script -> new GroovyScriptInfoDto(
                        script.scriptId(),
                        script.type(),
                        script.description(),
                        script.scriptBody(),
                        script.updatedAt(),
                        script.updatedBy()
                ))
                .toList();
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
        return groovyScriptService.executeAdvanced(scriptId, payload, Map.of(), Map.of(), subject);
    }

    @Post("/scripts/{scriptId}/execute-advanced")
    @Operation(summary = "Выполнить Groovy-скрипт (расширенный режим)", description = "Гарантированно передает параметры выполнения в binding: params/parameters/context.")
    public Object executeScriptAdvanced(HttpRequest<?> request,
                                        @PathVariable String scriptId,
                                        @Body ScriptExecutionRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        return groovyScriptService.executeAdvanced(
                scriptId,
                payload == null ? null : payload.payload(),
                payload == null || payload.parameters() == null ? Map.of() : payload.parameters(),
                payload == null || payload.context() == null ? Map.of() : payload.context(),
                subject
        );
    }

    @Post("/scripts/{scriptId}/debug")
    @Operation(summary = "Debug-выполнение скрипта", description = "Базовый инструмент отладки: выполняет скрипт и возвращает результат/ошибку с метрикой времени.")
    public Map<String, Object> debugScript(HttpRequest<?> request,
                                           @PathVariable String scriptId,
                                           @Body JsonNode payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        Instant startedAt = Instant.now();
        long started = System.currentTimeMillis();
        try {
            Object result = groovyScriptService.executeAdvanced(scriptId, payload, Map.of(), Map.of(), subject);
            return Map.of(
                    "ok", true,
                    "scriptId", scriptId,
                    "startedAt", startedAt.toString(),
                    "durationMs", System.currentTimeMillis() - started,
                    "result", result
            );
        } catch (Exception ex) {
            return Map.of(
                    "ok", false,
                    "scriptId", scriptId,
                    "startedAt", startedAt.toString(),
                    "durationMs", System.currentTimeMillis() - started,
                    "error", ex.getMessage()
            );
        }
    }

    @Post("/scripts/{scriptId}/debug-advanced")
    @Operation(summary = "Debug-выполнение скрипта (расширенный режим)", description = "Возвращает результат/ошибку и передает параметры выполнения в binding без потерь.")
    public Map<String, Object> debugScriptAdvanced(HttpRequest<?> request,
                                                   @PathVariable String scriptId,
                                                   @Body ScriptExecutionRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        Instant startedAt = Instant.now();
        long started = System.currentTimeMillis();
        try {
            Object result = groovyScriptService.executeAdvanced(
                    scriptId,
                    payload == null ? null : payload.payload(),
                    payload == null || payload.parameters() == null ? Map.of() : payload.parameters(),
                    payload == null || payload.context() == null ? Map.of() : payload.context(),
                    subject
            );
            return Map.of(
                    "ok", true,
                    "scriptId", scriptId,
                    "startedAt", startedAt.toString(),
                    "durationMs", System.currentTimeMillis() - started,
                    "parameters", payload == null || payload.parameters() == null ? Map.of() : payload.parameters(),
                    "result", result
            );
        } catch (Exception ex) {
            return Map.of(
                    "ok", false,
                    "scriptId", scriptId,
                    "startedAt", startedAt.toString(),
                    "durationMs", System.currentTimeMillis() - started,
                    "parameters", payload == null || payload.parameters() == null ? Map.of() : payload.parameters(),
                    "error", ex.getMessage()
            );
        }
    }

    @Post("/scripts/{scriptId}/validate")
    @Operation(summary = "Валидировать сохраненный скрипт", description = "Проверяет синтаксис Groovy-скрипта без фактического исполнения бизнес-логики.")
    public Map<String, Object> validateStoredScript(HttpRequest<?> request,
                                                    @PathVariable String scriptId) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        return groovyScriptService.validateScript(scriptId, subject);
    }

    @Post("/scripts/validate")
    @Operation(summary = "Валидировать текст скрипта", description = "Проверяет синтаксис перед сохранением (editor validate).")
    public Map<String, Object> validateScriptBody(HttpRequest<?> request,
                                                  @Body GroovyScriptUpsertRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return groovyScriptService.validateScriptBody(payload.type(), payload.scriptBody());
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

    @Post(value = "/templates/import/preview", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Предпросмотр ITS-архива", description = "Читает integration template архив (*.its) и возвращает структуру параметров/скриптов до импорта.")
    @ApiResponse(responseCode = "200", description = "Предпросмотр успешно построен",
            content = @Content(schema = @Schema(implementation = IntegrationTemplatePreviewDto.class)))
    public IntegrationTemplatePreviewDto previewTemplateImport(HttpRequest<?> request,
                                                               @Part("archive") CompletedFileUpload archive) throws IOException {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        return templateArchiveService.preview(archive.getBytes(), subject);
    }

    @Post(value = "/templates/import", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Импорт ITS-архива", description = "Импортирует programmable-обработчики из ITS, подставляя параметры из GUI/дефолтов template.yml.")
    public Map<String, Object> importTemplate(HttpRequest<?> request,
                                              @Part("archive") CompletedFileUpload archive,
                                              @Part("parameterValues") String parameterValues,
                                              @Part("replaceExisting") String replaceExisting) throws IOException {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        Map<String, String> values = parameterValues == null || parameterValues.isBlank()
                ? Map.of()
                : objectMapper.readValue(parameterValues, new TypeReference<>() {
                });
        boolean replace = "true".equalsIgnoreCase(replaceExisting);
        return templateArchiveService.importArchive(archive.getBytes(), values, replace, subject);
    }

    @Post(value = "/templates/export", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Экспорт ITS-архива", description = "Экспортирует выбранные programmable-обработчики во внешний ITS архив (zip с расширением .its).")
    public MutableHttpResponse<byte[]> exportTemplate(HttpRequest<?> request,
                                                      @Body IntegrationTemplateExportRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        byte[] archive = templateArchiveService.exportArchive(payload, subject);
        String fileName = (payload.templateId() == null || payload.templateId().isBlank()
                ? "integration-template"
                : payload.templateId()) + ".its";
        return HttpResponse.ok(archive)
                .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
    }
}
