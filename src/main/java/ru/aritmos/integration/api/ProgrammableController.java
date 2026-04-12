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
import io.micronaut.http.annotation.QueryValue;
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
import ru.aritmos.integration.domain.CustomerLookupRequest;
import ru.aritmos.integration.domain.CustomerMedicalServicesRequest;
import ru.aritmos.integration.domain.CustomerPrebookingRequest;
import ru.aritmos.integration.domain.ScriptExecutionRequest;
import ru.aritmos.integration.domain.StudioEditorSettingsDto;
import ru.aritmos.integration.domain.StudioOperationCatalogItemDto;
import ru.aritmos.integration.domain.StudioOperationRequest;
import ru.aritmos.integration.domain.ConnectorPublishRequest;
import ru.aritmos.integration.domain.ConnectorRestInvokeRequest;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.programming.CustomerMessageBusGateway;
import ru.aritmos.integration.programming.CustomerMessageBusAdapter;
import ru.aritmos.integration.programming.CustomerCrmIntegrationGateway;
import ru.aritmos.integration.programming.ExternalRestClient;
import ru.aritmos.integration.programming.GroovyScriptService;
import ru.aritmos.integration.programming.IntegrationTemplateArchiveService;
import ru.aritmos.integration.programming.ProgrammableEndpointService;
import ru.aritmos.integration.programming.ScriptDebugHistoryService;
import ru.aritmos.integration.programming.StudioEditorSettingsService;
import ru.aritmos.integration.programming.StudioOperationsService;
import ru.aritmos.integration.programming.StudioWorkspaceService;
import ru.aritmos.integration.security.RequestSecurityContext;
import ru.aritmos.integration.security.core.AuthorizationService;
import ru.aritmos.integration.service.VisitManagerMetricsService;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashSet;
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
    private final ExternalRestClient externalRestClient;
    private final CustomerCrmIntegrationGateway customerCrmIntegrationGateway;
    private final CustomerMessageBusGateway customerMessageBusGateway;
    private final List<CustomerMessageBusAdapter> messageBusAdapters;
    private final IntegrationGatewayConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;
    private final ScriptDebugHistoryService scriptDebugHistoryService;
    private final StudioWorkspaceService studioWorkspaceService;
    private final StudioEditorSettingsService studioEditorSettingsService;
    private final StudioOperationsService studioOperationsService;
    private final VisitManagerMetricsService visitManagerMetricsService;

    public ProgrammableController(ProgrammableEndpointService programmableEndpointService,
                                  GroovyScriptService groovyScriptService,
                                  IntegrationTemplateArchiveService templateArchiveService,
                                  ExternalRestClient externalRestClient,
                                  CustomerCrmIntegrationGateway customerCrmIntegrationGateway,
                                  CustomerMessageBusGateway customerMessageBusGateway,
                                  List<CustomerMessageBusAdapter> messageBusAdapters,
                                  IntegrationGatewayConfiguration configuration,
                                  ObjectMapper objectMapper,
                                  AuthorizationService authorizationService,
                                  ScriptDebugHistoryService scriptDebugHistoryService,
                                  StudioWorkspaceService studioWorkspaceService,
                                  StudioEditorSettingsService studioEditorSettingsService,
                                  StudioOperationsService studioOperationsService,
                                  VisitManagerMetricsService visitManagerMetricsService) {
        this.programmableEndpointService = programmableEndpointService;
        this.groovyScriptService = groovyScriptService;
        this.templateArchiveService = templateArchiveService;
        this.externalRestClient = externalRestClient;
        this.customerCrmIntegrationGateway = customerCrmIntegrationGateway;
        this.customerMessageBusGateway = customerMessageBusGateway;
        this.messageBusAdapters = messageBusAdapters;
        this.configuration = configuration;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.scriptDebugHistoryService = scriptDebugHistoryService;
        this.studioWorkspaceService = studioWorkspaceService;
        this.studioEditorSettingsService = studioEditorSettingsService;
        this.studioOperationsService = studioOperationsService;
        this.visitManagerMetricsService = visitManagerMetricsService;
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
        Map<String, Object> payloadMap = payload == null
                ? Map.of()
                : objectMapper.convertValue(payload, new TypeReference<>() {
                });
        try {
            Object result = groovyScriptService.executeAdvanced(scriptId, payload, Map.of(), Map.of(), subject);
            scriptDebugHistoryService.record(new ScriptDebugHistoryService.DebugEntry(
                    scriptId,
                    startedAt,
                    System.currentTimeMillis() - started,
                    true,
                    result,
                    null,
                    payloadMap,
                    Map.of(),
                    Map.of()
            ));
            return Map.of(
                    "ok", true,
                    "scriptId", scriptId,
                    "startedAt", startedAt.toString(),
                    "durationMs", System.currentTimeMillis() - started,
                    "result", result
            );
        } catch (Exception ex) {
            scriptDebugHistoryService.record(new ScriptDebugHistoryService.DebugEntry(
                    scriptId,
                    startedAt,
                    System.currentTimeMillis() - started,
                    false,
                    null,
                    ex.getMessage(),
                    payloadMap,
                    Map.of(),
                    Map.of()
            ));
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
        Map<String, Object> parameters = payload == null || payload.parameters() == null ? Map.of() : payload.parameters();
        Map<String, Object> context = payload == null || payload.context() == null ? Map.of() : payload.context();
        Map<String, Object> safePayload = payload == null || payload.payload() == null
                ? Map.of()
                : objectMapper.convertValue(payload.payload(), new TypeReference<>() {
                });
        try {
            Object result = groovyScriptService.executeAdvanced(
                    scriptId,
                    payload == null ? null : payload.payload(),
                    parameters,
                    context,
                    subject
            );
            scriptDebugHistoryService.record(new ScriptDebugHistoryService.DebugEntry(
                    scriptId,
                    startedAt,
                    System.currentTimeMillis() - started,
                    true,
                    result,
                    null,
                    safePayload,
                    parameters,
                    context
            ));
            return Map.of(
                    "ok", true,
                    "scriptId", scriptId,
                    "startedAt", startedAt.toString(),
                    "durationMs", System.currentTimeMillis() - started,
                    "parameters", parameters,
                    "result", result
            );
        } catch (Exception ex) {
            scriptDebugHistoryService.record(new ScriptDebugHistoryService.DebugEntry(
                    scriptId,
                    startedAt,
                    System.currentTimeMillis() - started,
                    false,
                    null,
                    ex.getMessage(),
                    safePayload,
                    parameters,
                    context
            ));
            return Map.of(
                    "ok", false,
                    "scriptId", scriptId,
                    "startedAt", startedAt.toString(),
                    "durationMs", System.currentTimeMillis() - started,
                    "parameters", parameters,
                    "error", ex.getMessage()
            );
        }
    }

    @Get("/scripts/debug/history")
    @Operation(summary = "История debug запусков", description = "Возвращает in-memory историю debug/execute запусков скриптов для IDE-панели.")
    public List<ScriptDebugHistoryService.DebugEntry> debugHistory(HttpRequest<?> request,
                                                                   @QueryValue(defaultValue = "") String scriptId,
                                                                   @QueryValue(defaultValue = "50") int limit) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return scriptDebugHistoryService.list(scriptId, limit);
    }

    @Delete("/scripts/debug/history")
    @Operation(summary = "Очистить историю debug", description = "Очищает всю debug-историю или только по конкретному scriptId.")
    public Map<String, Object> clearDebugHistory(HttpRequest<?> request,
                                                 @QueryValue(defaultValue = "") String scriptId) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-manage");
        int removed = scriptDebugHistoryService.clear(scriptId);
        return Map.of("removed", removed, "scriptId", scriptId);
    }

    @Post("/scripts/{scriptId}/debug/replay-last")
    @Operation(summary = "Повторить последний debug-запуск", description = "Повторно запускает скрипт с payload/parameters/context из последней debug-записи.")
    public Map<String, Object> replayLastDebug(HttpRequest<?> request,
                                               @PathVariable String scriptId) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        ScriptDebugHistoryService.DebugEntry entry = scriptDebugHistoryService.latest(scriptId);
        if (entry == null) {
            throw new IllegalArgumentException("Нет debug history для scriptId: " + scriptId);
        }
        Instant startedAt = Instant.now();
        long started = System.currentTimeMillis();
        JsonNode payload = objectMapper.valueToTree(entry.payload() == null ? Map.of() : entry.payload());
        try {
            Object result = groovyScriptService.executeAdvanced(
                    scriptId,
                    payload,
                    entry.parameters() == null ? Map.of() : entry.parameters(),
                    entry.context() == null ? Map.of() : entry.context(),
                    subject
            );
            scriptDebugHistoryService.record(new ScriptDebugHistoryService.DebugEntry(
                    scriptId,
                    startedAt,
                    System.currentTimeMillis() - started,
                    true,
                    result,
                    null,
                    entry.payload() == null ? Map.of() : entry.payload(),
                    entry.parameters() == null ? Map.of() : entry.parameters(),
                    entry.context() == null ? Map.of() : entry.context()
            ));
            return Map.of(
                    "ok", true,
                    "scriptId", scriptId,
                    "startedAt", startedAt.toString(),
                    "durationMs", System.currentTimeMillis() - started,
                    "result", result
            );
        } catch (Exception ex) {
            scriptDebugHistoryService.record(new ScriptDebugHistoryService.DebugEntry(
                    scriptId,
                    startedAt,
                    System.currentTimeMillis() - started,
                    false,
                    null,
                    ex.getMessage(),
                    entry.payload() == null ? Map.of() : entry.payload(),
                    entry.parameters() == null ? Map.of() : entry.parameters(),
                    entry.context() == null ? Map.of() : entry.context()
            ));
            return Map.of(
                    "ok", false,
                    "scriptId", scriptId,
                    "startedAt", startedAt.toString(),
                    "durationMs", System.currentTimeMillis() - started,
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

    @Get("/connectors/catalog")
    @Operation(summary = "Каталог коннекторов", description = "Возвращает список подключенных REST-сервисов, брокеров и реакций message bus для GUI/операторов.")
    public Map<String, Object> connectorsCatalog(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");

        var restServices = configuration.getProgrammableApi().getExternalRestServices().stream()
                .map(item -> Map.of(
                        "id", item.getId(),
                        "baseUrl", item.getBaseUrl(),
                        "defaultHeadersConfigured", item.getDefaultHeaders() != null && !item.getDefaultHeaders().isEmpty()
                ))
                .toList();
        var messageBrokers = configuration.getProgrammableApi().getMessageBrokers().stream()
                .map(item -> Map.of(
                        "id", item.getId(),
                        "type", item.getType(),
                        "enabled", item.isEnabled()
                ))
                .toList();
        var messageReactions = configuration.getProgrammableApi().getMessageReactions().stream()
                .map(item -> Map.of(
                        "brokerId", item.getBrokerId(),
                        "topic", item.getTopic(),
                        "scriptId", item.getScriptId()
                ))
                .toList();

        return Map.of(
                "externalRestServices", restServices,
                "messageBrokers", messageBrokers,
                "messageReactions", messageReactions,
                "supportedBrokerTypes", supportedBrokerTypes(),
                "supportedBrokerProfiles", supportedBrokerProfiles()
        );
    }

    @Get("/connectors/broker-types")
    @Operation(summary = "Поддерживаемые типы брокеров", description = "Возвращает объединенный список типов брокеров, поддерживаемых зарегистрированными adapter-ами.")
    public Map<String, Object> supportedBrokerTypesCatalog(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return Map.of(
                "supportedBrokerTypes", supportedBrokerTypes(),
                "supportedBrokerProfiles", supportedBrokerProfiles()
        );
    }

    @Get("/connectors/health")
    @Operation(summary = "Проверка здоровья коннекторов", description = "Возвращает lightweight диагностику конфигурации REST/брокер коннекторов.")
    public Map<String, Object> connectorsHealth(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        var rest = configuration.getProgrammableApi().getExternalRestServices().stream()
                .map(item -> Map.of(
                        "id", item.getId(),
                        "ok", item.getBaseUrl() != null && !item.getBaseUrl().isBlank(),
                        "baseUrl", item.getBaseUrl() == null ? "" : item.getBaseUrl()
                ))
                .toList();
        var brokers = configuration.getProgrammableApi().getMessageBrokers().stream()
                .map(item -> Map.of(
                        "id", item.getId(),
                        "type", item.getType(),
                        "enabled", item.isEnabled(),
                        "adapterAvailable", messageBusAdapters.stream().anyMatch(adapter -> adapter.supports(item.getType()))
                ))
                .toList();
        boolean allOk = rest.stream().allMatch(item -> Boolean.TRUE.equals(item.get("ok")))
                && brokers.stream().allMatch(item -> !Boolean.TRUE.equals(item.get("enabled")) || Boolean.TRUE.equals(item.get("adapterAvailable")));
        return Map.of(
                "ok", allOk,
                "restServices", rest,
                "brokers", brokers,
                "checkedAt", Instant.now().toString()
        );
    }

    @Get("/studio/bootstrap")
    @Operation(summary = "Bootstrap данные programmable-студии",
            description = "Возвращает сводную информацию для GUI: inbox/outbox, runtime Groovy, коннекторы, IDE editor и настройки.")
    public Map<String, Object> studioBootstrap(HttpRequest<?> request,
                                               @QueryValue(defaultValue = "20") int debugHistoryLimit) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        Map<String, Object> response = new java.util.LinkedHashMap<>(studioWorkspaceService.buildWorkspaceSnapshot(debugHistoryLimit));
        response.put("editorSettings", studioEditorSettingsService.get(subject.subjectId()));
        response.put("editorCapabilities", studioEditorSettingsService.capabilities());
        return response;
    }

    @Get("/studio/dashboard")
    @Operation(summary = "Сводный dashboard programmable-студии",
            description = "GUI-friendly сводка: dashboard snapshot, connectors health и метрики VisitManager.")
    public Map<String, Object> studioDashboard(HttpRequest<?> request,
                                               @QueryValue(defaultValue = "20") int debugHistoryLimit) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return Map.of(
                "dashboard", studioWorkspaceService.buildDashboardSnapshot(debugHistoryLimit),
                "connectorsHealth", connectorsHealth(request),
                "visitManagerMetrics", visitManagerMetricsService.snapshot(),
                "generatedAt", Instant.now().toString()
        );
    }

    @Get("/studio/settings")
    @Operation(summary = "Получить настройки IDE-редактора", description = "Возвращает персональные настройки редактора programmable-студии.")
    public StudioEditorSettingsDto getStudioSettings(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return studioEditorSettingsService.get(subject.subjectId());
    }

    @Put("/studio/settings")
    @Operation(summary = "Сохранить настройки IDE-редактора", description = "Сохраняет персональные настройки редактора programmable-студии.")
    public StudioEditorSettingsDto saveStudioSettings(HttpRequest<?> request,
                                                      @Body StudioEditorSettingsDto payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return studioEditorSettingsService.save(subject.subjectId(), payload);
    }

    @Get("/studio/capabilities")
    @Operation(summary = "Capabilities IDE-настроек", description = "Возвращает ограничения и поддерживаемые параметры IDE-редактора.")
    public Map<String, Object> studioSettingsCapabilities(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return studioEditorSettingsService.capabilities();
    }

    @Get("/studio/settings/export")
    @Operation(summary = "Экспорт настроек IDE-редактора",
            description = "Экспортирует сохраненные настройки IDE для backup/restore через GUI.")
    public Map<String, Object> exportStudioSettings(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return Map.of(
                "settingsBySubject", studioEditorSettingsService.exportAll(),
                "capabilities", studioEditorSettingsService.capabilities(),
                "exportedAt", Instant.now().toString()
        );
    }

    @Post("/studio/settings/import")
    @Operation(summary = "Импорт настроек IDE-редактора",
            description = "Импортирует настройки IDE из GUI backup (merge/replace режим).")
    public Map<String, Object> importStudioSettings(HttpRequest<?> request,
                                                    @Body Map<String, Object> payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-manage");
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        boolean replaceExisting = Boolean.TRUE.equals(safePayload.get("replaceExisting"));
        Map<String, StudioEditorSettingsDto> settingsBySubject = objectMapper.convertValue(
                safePayload.getOrDefault("settingsBySubject", Map.of()),
                new TypeReference<Map<String, StudioEditorSettingsDto>>() {
                }
        );
        Map<String, Object> result = new java.util.LinkedHashMap<>(
                studioEditorSettingsService.importAll(settingsBySubject, replaceExisting));
        result.put("importedAt", Instant.now().toString());
        return result;
    }

    @Get("/studio/playbook")
    @Operation(summary = "Playbook programmable-студии",
            description = "Пошаговый операционный playbook с акцентом на основные задачи интеграции СУО с внешними службами заказчика "
                    + "(connectors health, routing, queue smoke, branch-state sync, external REST/message bus). "
                    + "Поддерживает сортировку по важности (sortBy=importance, по умолчанию) или исходному порядку (sortBy=order).")
    public List<Map<String, Object>> studioPlaybook(HttpRequest<?> request,
                                                    @QueryValue(defaultValue = "importance") String sortBy) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return studioWorkspaceService.buildPlaybook(sortBy);
    }

    @Get("/studio/operations/catalog")
    @Operation(summary = "Каталог studio operations",
            description = "Возвращает поддерживаемые operation-коды и шаблоны параметров для GUI.")
    public List<StudioOperationCatalogItemDto> studioOperationsCatalog(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return studioOperationsService.catalog();
    }

    @Post("/studio/operations")
    @Operation(summary = "Выполнить служебную операцию studio",
            description = "Единая точка для GUI-операций: eventing maintenance, snapshots, bootstrap refresh и export IDE settings.")
    public Map<String, Object> executeStudioOperation(HttpRequest<?> request,
                                                      @Body StudioOperationRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        if (payload == null || payload.operation() == null || payload.operation().isBlank()) {
            throw new IllegalArgumentException("operation обязателен");
        }
        return studioOperationsService.execute(payload.operation(), payload.parameters(), subject.subjectId());
    }

    @Post("/connectors/rest/invoke")
    @Operation(summary = "Ручной вызов внешнего REST-сервиса", description = "Операционный invoke через настроенный REST-коннектор заказчика.")
    public Map<String, Object> invokeExternalRest(HttpRequest<?> request,
                                                  @Body ConnectorRestInvokeRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return externalRestClient.invoke(
                payload.serviceId(),
                payload.method(),
                payload.path(),
                payload.body() == null ? Map.of() : payload.body(),
                payload.headers() == null ? Map.of() : payload.headers()
        );
    }

    @Post("/connectors/bus/publish")
    @Operation(summary = "Ручная публикация в брокер/шину", description = "Операционный publish через настроенный adapter message bus.")
    public Map<String, Object> publishMessage(HttpRequest<?> request,
                                              @Body ConnectorPublishRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return customerMessageBusGateway.publish(
                payload.brokerId(),
                payload.topic(),
                payload.key(),
                payload.payload() == null ? Map.of() : payload.payload(),
                payload.headers() == null ? Map.of() : payload.headers()
        );
    }

    @Post("/connectors/crm/identify-client")
    @Operation(summary = "Идентификация клиента во внешней CRM",
            description = "Поиск клиента по идентификационной строке (телефон, СНИЛС, ИНН и др.) через настроенный CRM connector.")
    public Map<String, Object> identifyClient(HttpRequest<?> request,
                                              @Body CustomerLookupRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return customerCrmIntegrationGateway.identifyClient(payload);
    }

    @Post("/connectors/crm/medical-services")
    @Operation(summary = "Получить доступные медицинские услуги для клиента",
            description = "Запрашивает перечень услуг во внешней CRM/МИС по идентификационной строке клиента.")
    public Map<String, Object> availableMedicalServices(HttpRequest<?> request,
                                                        @Body CustomerMedicalServicesRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return customerCrmIntegrationGateway.fetchAvailableMedicalServices(payload);
    }

    @Post("/connectors/crm/prebooking")
    @Operation(summary = "Получить данные предварительной записи клиента",
            description = "Запрашивает pre-booking/предзапись во внешней CRM/МИС по идентификационной строке клиента.")
    public Map<String, Object> prebookingData(HttpRequest<?> request,
                                              @Body CustomerPrebookingRequest payload) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return customerCrmIntegrationGateway.fetchPrebookingData(payload);
    }

    private List<String> supportedBrokerTypes() {
        LinkedHashSet<String> supported = new LinkedHashSet<>();
        messageBusAdapters.forEach(adapter -> supported.addAll(adapter.supportedBrokerTypes()));
        return supported.stream().sorted().toList();
    }

    private List<Map<String, Object>> supportedBrokerProfiles() {
        return messageBusAdapters.stream()
                .flatMap(adapter -> adapter.supportedBrokerProfiles().stream())
                .filter(item -> item.containsKey("type"))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(
                                item -> String.valueOf(item.get("type")).trim().toUpperCase(),
                                item -> item,
                                (left, right) -> left,
                                java.util.LinkedHashMap::new
                        ),
                        map -> List.copyOf(map.values())
                ));
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

    @Post(value = "/templates/import/validate", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Валидировать ITS-архив", description = "Проверяет структуру template, script types и согласованность параметров до импорта.")
    public Map<String, Object> validateTemplateImport(HttpRequest<?> request,
                                                      @Part("archive") CompletedFileUpload archive) throws IOException {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
        return templateArchiveService.validateArchive(archive.getBytes(), subject);
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
