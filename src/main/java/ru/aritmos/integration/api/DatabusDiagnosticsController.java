package ru.aritmos.integration.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integration.error.UnauthorizedException;
import ru.aritmos.integration.eventing.EventDispatcherService;
import ru.aritmos.integration.eventing.EventRetryService;
import ru.aritmos.integration.eventing.EventingStats;
import ru.aritmos.integration.eventing.IntegrationEvent;
import ru.aritmos.integration.security.RequestSecurityContext;
import ru.aritmos.integration.security.core.AuthorizationService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

/**
 * Совместимые диагностические endpoints для legacy UI DataBus.
 */
@Controller("/databus/diagnostics")
@Tag(name = "Eventing", description = "Legacy-совместимые диагностические endpoints DataBus")
public class DatabusDiagnosticsController {

    private final EventDispatcherService dispatcherService;
    private final EventRetryService retryService;
    private final AuthorizationService authorizationService;

    public DatabusDiagnosticsController(EventDispatcherService dispatcherService,
                                        EventRetryService retryService,
                                        AuthorizationService authorizationService) {
        this.dispatcherService = dispatcherService;
        this.retryService = retryService;
        this.authorizationService = authorizationService;
    }

    @Get("/dashboard")
    @Operation(summary = "Legacy dashboard", description = "Возвращает совместимый диагностический снимок для старого DataBus UI.")
    public Map<String, Object> dashboard(HttpRequest<?> request,
                                         @QueryValue(defaultValue = "12") int targetLimit,
                                         @QueryValue(defaultValue = "false") boolean includeTopTargets) {
        authorize(request);
        EventingStats stats = dispatcherService.stats();
        int normalizedLimit = Math.max(1, targetLimit);
        return Map.of(
                "processedCount", stats.processedCount(),
                "duplicateCount", stats.duplicateCount(),
                "dlqSize", stats.dlqSize(),
                "outboxPendingSize", stats.outboxPendingSize(),
                "outboxFailedSize", stats.outboxFailedSize(),
                "outboxDeadSize", stats.outboxDeadSize(),
                "topTargets", includeTopTargets ? buildTopTargets(normalizedLimit) : List.of(),
                "targetLimit", normalizedLimit
        );
    }

    @Get("/messages")
    @Operation(summary = "Legacy messages", description = "Возвращает последние сообщения для старого DataBus UI без ошибок 404.")
    public List<Map<String, Object>> messages(HttpRequest<?> request,
                                              @QueryValue(defaultValue = "5000") int limit,
                                              @QueryValue(defaultValue = "true") boolean includePayload) {
        authorize(request);
        return Stream.concat(
                        dispatcherService.processedEvents().values().stream(),
                        retryService.dlqSnapshot().stream())
                .sorted(Comparator.comparing(IntegrationEvent::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(Math.max(1, limit))
                .map(event -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("eventId", event.eventId());
                    row.put("eventType", event.eventType());
                    row.put("source", event.source());
                    row.put("occurredAt", event.occurredAt());
                    row.put("payload", includePayload ? event.payload() : Map.of());
                    return row;
                })
                .toList();
    }

    private void authorize(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
    }

    private List<Map<String, Object>> buildTopTargets(int limit) {
        Map<String, Long> sourceCounters = new java.util.HashMap<>();
        Stream.concat(
                        dispatcherService.processedEvents().values().stream(),
                        retryService.dlqSnapshot().stream())
                .map(event -> event.source() == null || event.source().isBlank() ? "unknown" : event.source())
                .forEach(source -> sourceCounters.merge(source, 1L, Long::sum));
        return sourceCounters.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("target", entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
                .toList();
    }
}
