package ru.aritmos.integration.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import ru.aritmos.integration.domain.AggregatedQueuesResponse;
import ru.aritmos.integration.domain.BranchStateDto;
import ru.aritmos.integration.domain.BranchStateUpdateRequest;
import ru.aritmos.integration.domain.CallVisitorRequest;
import ru.aritmos.integration.domain.CallVisitorResponse;
import ru.aritmos.integration.domain.ErrorResponse;
import ru.aritmos.integration.domain.QueueListResponse;
import ru.aritmos.integration.domain.VisitManagerMetricDto;
import ru.aritmos.integration.security.RequestSecurityContext;
import ru.aritmos.integration.security.core.AuthorizationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;
import ru.aritmos.integration.service.GatewayService;
import ru.aritmos.integration.service.VisitManagerMetricsService;

import java.util.Arrays;
import java.util.List;

/**
 * Контроллер унифицированного REST API для операций VisitManager.
 */
@Validated
@Controller("/api/v1")
@Tag(name = "Integration Gateway", description = "Интеграционные операции с VisitManager")
public class GatewayController {

    private final GatewayService gatewayService;
    private final VisitManagerMetricsService metricsService;
    private final AuthorizationService authorizationService;

    public GatewayController(GatewayService gatewayService,
                             VisitManagerMetricsService metricsService,
                             AuthorizationService authorizationService) {
        this.gatewayService = gatewayService;
        this.metricsService = metricsService;
        this.authorizationService = authorizationService;
    }

    @Get("/queues{?branchId,target}")
    @Operation(summary = "Получить очереди по филиалу", description = "Возвращает нормализованный список очередей, используя кеш и маршрутизацию.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список очередей получен.",
                    content = @Content(schema = @Schema(implementation = QueueListResponse.class))),
            @ApiResponse(responseCode = "401", description = "Субъект не аутентифицирован.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав queue-view.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public QueueListResponse getQueues(HttpRequest<?> request,
                                       @Parameter(description = "Идентификатор отделения VisitManager.", required = true)
                                       @NotBlank @QueryValue String branchId,
                                       @Parameter(description = "Явный target VisitManager. Если пусто, используется routing.")
                                       @QueryValue(defaultValue = "") String target) {
        SubjectPrincipal subject = subject(request);
        authorizationService.requirePermission(subject, "queue-view");
        return gatewayService.getQueues(subject.subjectId(), branchId, target);
    }

    @Get("/queues/aggregate{?branchIds}")
    @Operation(summary = "Агрегировать очереди по нескольким филиалам", description = "Выполняет fan-out вызовы к нескольким VisitManager, возвращая partial результат при недоступности части контуров.")
    public AggregatedQueuesResponse aggregateQueues(HttpRequest<?> request,
                                                    @NotBlank @QueryValue String branchIds) {
        SubjectPrincipal subject = subject(request);
        authorizationService.requirePermission(subject, "queue-aggregate");
        List<String> parsedBranches = Arrays.stream(branchIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        return gatewayService.getAggregatedQueues(subject.subjectId(), parsedBranches);
    }

    @Post("/visitors/{visitorId}/call{?target}")
    @Operation(summary = "Вызвать посетителя", description = "Проксирует команду вызова в соответствующий VisitManager с аудитом и инвалидацией кеша.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Посетитель вызван.",
                    content = @Content(schema = @Schema(implementation = CallVisitorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректное тело команды вызова.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав queue-call.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CallVisitorResponse callVisitor(HttpRequest<?> request,
                                           @Parameter(description = "Идентификатор визита/посетителя.", required = true)
                                           @PathVariable String visitorId,
                                           @Valid @Body CallVisitorRequest body,
                                           @QueryValue(defaultValue = "") String target) {
        SubjectPrincipal subject = subject(request);
        authorizationService.requirePermission(subject, "queue-call");
        return gatewayService.callVisitor(subject.subjectId(), visitorId, body, target);
    }

    @Get("/branches/{branchId}/state{?target}")
    @Operation(summary = "Получить состояние отделения", description = "Возвращает состояние отделения из кэша, при промахе запрашивает VisitManager.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состояние отделения получено.",
                    content = @Content(schema = @Schema(implementation = BranchStateDto.class))),
            @ApiResponse(responseCode = "404", description = "Отделение не найдено.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BranchStateDto branchState(HttpRequest<?> request,
                                      @PathVariable String branchId,
                                      @QueryValue(defaultValue = "") String target) {
        SubjectPrincipal subject = subject(request);
        authorizationService.requirePermission(subject, "branch-state-view");
        return gatewayService.getBranchState(subject.subjectId(), branchId, target);
    }

    @Post("/branches/{branchId}/state/refresh{?target}")
    @Operation(summary = "Принудительно обновить состояние отделения", description = "Синхронизирует кэш состояния отделения с VisitManager независимо от текущего cache-hit.")
    public BranchStateDto refreshBranchState(HttpRequest<?> request,
                                             @PathVariable String branchId,
                                             @QueryValue(defaultValue = "") String target) {
        SubjectPrincipal subject = subject(request);
        authorizationService.requirePermission(subject, "branch-state-view");
        return gatewayService.refreshBranchState(subject.subjectId(), branchId, target);
    }

    @Put("/branches/{branchId}/state{?target}")
    @Operation(summary = "Обновить состояние отделения", description = "Проксирует обновление состояния отделения в VisitManager и актуализирует локальный кэш.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состояние отделения обновлено.",
                    content = @Content(schema = @Schema(implementation = BranchStateDto.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации body.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав branch-state-manage.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BranchStateDto updateBranchState(HttpRequest<?> request,
                                            @PathVariable String branchId,
                                            @Valid @Body BranchStateUpdateRequest body,
                                            @QueryValue(defaultValue = "") String target) {
        SubjectPrincipal subject = subject(request);
        authorizationService.requirePermission(subject, "branch-state-manage");
        return gatewayService.updateBranchState(subject.subjectId(), branchId, body, target);
    }

    @Get("/branches/states")
    @Operation(summary = "Снимок кэша состояний отделений", description = "Возвращает текущий in-memory снимок cache актуальных состояний отделений.")
    public List<BranchStateDto> branchStatesSnapshot(HttpRequest<?> request) {
        SubjectPrincipal subject = subject(request);
        authorizationService.requirePermission(subject, "branch-state-view");
        return gatewayService.branchStateSnapshot();
    }

    @Get("/metrics/visit-managers")
    @Operation(summary = "Метрики вызовов VisitManager", description = "Возвращает счетчики успешных и ошибочных обращений по каждому target VisitManager.")
    public List<VisitManagerMetricDto> metrics(HttpRequest<?> request) {
        SubjectPrincipal subject = subject(request);
        authorizationService.requirePermission(subject, "metrics-view");
        return metricsService.snapshot();
    }

    private SubjectPrincipal subject(HttpRequest<?> request) {
        return RequestSecurityContext.current(request)
                .orElseThrow(() -> new SecurityException("Субъект не аутентифицирован"));
    }
}
