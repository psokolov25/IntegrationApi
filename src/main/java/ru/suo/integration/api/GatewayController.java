package ru.suo.integration.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import ru.suo.integration.domain.AggregatedQueuesResponse;
import ru.suo.integration.domain.CallVisitorRequest;
import ru.suo.integration.domain.CallVisitorResponse;
import ru.suo.integration.domain.QueueListResponse;
import ru.suo.integration.domain.VisitManagerMetricDto;
import ru.suo.integration.security.RequestSecurityContext;
import ru.suo.integration.security.core.AuthorizationService;
import ru.suo.integration.security.core.SubjectPrincipal;
import ru.suo.integration.service.GatewayService;
import ru.suo.integration.service.VisitManagerMetricsService;

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
    public QueueListResponse getQueues(HttpRequest<?> request,
                                       @NotBlank @QueryValue String branchId,
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
    public CallVisitorResponse callVisitor(HttpRequest<?> request,
                                           @PathVariable String visitorId,
                                           @Valid @Body CallVisitorRequest body,
                                           @QueryValue(defaultValue = "") String target) {
        SubjectPrincipal subject = subject(request);
        authorizationService.requirePermission(subject, "queue-call");
        return gatewayService.callVisitor(subject.subjectId(), visitorId, body, target);
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
