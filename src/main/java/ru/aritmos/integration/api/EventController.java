package ru.aritmos.integration.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integration.error.NotFoundException;
import ru.aritmos.integration.error.UnauthorizedException;
import ru.aritmos.integration.error.ValidationException;
import ru.aritmos.integration.eventing.EventDispatcherService;
import ru.aritmos.integration.eventing.EventingCapabilities;
import ru.aritmos.integration.eventing.EventingHealth;
import ru.aritmos.integration.eventing.EventingImportAnalysis;
import ru.aritmos.integration.eventing.EventingImportResult;
import ru.aritmos.integration.eventing.EventingLimits;
import ru.aritmos.integration.eventing.EventingMaintenanceReport;
import ru.aritmos.integration.eventing.EventingSnapshot;
import ru.aritmos.integration.eventing.EventingSnapshotValidation;
import ru.aritmos.integration.eventing.EventProcessingResult;
import ru.aritmos.integration.eventing.EventRetryService;
import ru.aritmos.integration.eventing.EventingStats;
import ru.aritmos.integration.eventing.IntegrationEvent;
import ru.aritmos.integration.security.RequestSecurityContext;
import ru.aritmos.integration.security.core.AuthorizationService;

import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Objects;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Event ingestion API для этапа 6.
 */
@Controller("/api/v1/events")
@Tag(name = "Eventing", description = "Event-driven integration endpoints")
public class EventController {

    private final EventDispatcherService dispatcherService;
    private final EventRetryService retryService;
    private final AuthorizationService authorizationService;

    public EventController(EventDispatcherService dispatcherService,
                           EventRetryService retryService,
                           AuthorizationService authorizationService) {
        this.dispatcherService = dispatcherService;
        this.retryService = retryService;
        this.authorizationService = authorizationService;
    }

    @Post("/ingest")
    @Operation(summary = "Принять событие", description = "Обработка через ingestion/validation/idempotency/retry/DLQ pipeline.")
    public EventProcessingResult ingest(HttpRequest<?> request, @Body IntegrationEvent event) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.process(event);
    }


    @Post("/replay/{eventId}")
    @Operation(summary = "Replay события", description = "Повторная обработка ранее успешно сохраненного события.")
    public EventProcessingResult replay(HttpRequest<?> request, @PathVariable String eventId) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.replay(eventId);
    }

    @Post("/replay-dlq/{eventId}")
    @Operation(summary = "Replay события из DLQ", description = "Повторная обработка события, попавшего в DLQ.")
    public EventProcessingResult replayDlq(HttpRequest<?> request, @PathVariable String eventId) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.replayFromDlq(eventId);
    }

    @Get("/dlq")
    @Operation(summary = "DLQ snapshot", description = "Текущий список событий в DLQ (in-memory).")
    public List<IntegrationEvent> dlq(HttpRequest<?> request,
                                      @QueryValue(defaultValue = "") String eventType,
                                      @QueryValue(defaultValue = "") String source,
                                      @QueryValue(defaultValue = "100") int limit,
                                      @QueryValue(defaultValue = "0") int offset,
                                      @QueryValue(defaultValue = "") String from,
                                      @QueryValue(defaultValue = "") String to,
                                      @QueryValue(defaultValue = "false") boolean desc) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return filterEvents(retryService.dlqSnapshot().stream(), eventType, source, limit, offset, from, to, desc);
    }

    @Get("/dlq/{eventId}")
    @Operation(summary = "Получить событие из DLQ", description = "Возвращает одно событие по eventId из in-memory DLQ.")
    public IntegrationEvent dlqById(HttpRequest<?> request, @PathVariable String eventId) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        IntegrationEvent event = retryService.getById(eventId);
        if (event == null) {
            throw new NotFoundException("Событие в DLQ не найдено: " + eventId);
        }
        return event;
    }

    @Delete("/dlq/{eventId}")
    @Operation(summary = "Удалить событие из DLQ", description = "Удаляет событие из DLQ по eventId.")
    public Map<String, Object> removeFromDlq(HttpRequest<?> request, @PathVariable String eventId) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        boolean existed = retryService.contains(eventId);
        retryService.remove(eventId);
        return Map.of("eventId", eventId, "removed", existed);
    }

    @Delete("/dlq")
    @Operation(summary = "Очистить DLQ", description = "Удаляет все события из in-memory DLQ.")
    public Map<String, Object> clearDlq(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        int before = retryService.size();
        retryService.clear();
        return Map.of("removed", before);
    }

    @Get("/processed")
    @Operation(summary = "Список обработанных событий", description = "Снимок in-memory replay store.")
    public List<IntegrationEvent> processed(HttpRequest<?> request,
                                            @QueryValue(defaultValue = "") String eventType,
                                            @QueryValue(defaultValue = "") String source,
                                            @QueryValue(defaultValue = "100") int limit,
                                            @QueryValue(defaultValue = "0") int offset,
                                            @QueryValue(defaultValue = "") String from,
                                            @QueryValue(defaultValue = "") String to,
                                            @QueryValue(defaultValue = "false") boolean desc) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return filterEvents(dispatcherService.processedEvents().values().stream(), eventType, source, limit, offset, from, to, desc);
    }

    @Get("/processed/{eventId}")
    @Operation(summary = "Получить обработанное событие", description = "Возвращает событие из replay store по eventId.")
    public IntegrationEvent processedById(HttpRequest<?> request, @PathVariable String eventId) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        IntegrationEvent event = dispatcherService.processedEvent(eventId);
        if (event == null) {
            throw new NotFoundException("Событие в replay store не найдено: " + eventId);
        }
        return event;
    }

    @Get("/stats")
    @Operation(summary = "Статистика eventing", description = "Операционные метрики eventing pipeline (in-memory).")
    public EventingStats stats(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.stats();
    }

    @Get("/stats/health")
    @Operation(summary = "Здоровье eventing", description = "Оценка состояния eventing по порогам DLQ/duplicate.")
    public EventingHealth health(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.health();
    }

    @Post("/replay-dlq")
    @Operation(summary = "Bulk replay из DLQ", description = "Повторно обрабатывает до N событий из DLQ.")
    public List<EventProcessingResult> replayDlqBulk(HttpRequest<?> request,
                                                     @QueryValue(defaultValue = "100") int limit) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.replayAllFromDlq(limit);
    }

    @Delete("/processed")
    @Operation(summary = "Очистить processed store", description = "Очищает in-memory replay store и inbox idempotency.")
    public Map<String, Object> clearProcessed(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        int before = dispatcherService.stats().processedStoreSize();
        dispatcherService.clearProcessedStore();
        return Map.of("removed", before);
    }

    @Delete("/stats")
    @Operation(summary = "Сброс статистики eventing", description = "Сбрасывает in-memory счетчики eventing pipeline.")
    public Map<String, Object> resetStats(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        dispatcherService.resetStats();
        return Map.of("status", "OK");
    }

    @Post("/maintenance/run")
    @Operation(summary = "Запустить maintenance eventing", description = "Prune/trim in-memory DLQ и processed store по retention/threshold policy.")
    public EventingMaintenanceReport runMaintenance(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.runMaintenance();
    }

    @Get("/maintenance/preview")
    @Operation(summary = "Предпросмотр maintenance", description = "Показывает сколько записей будет удалено без фактических изменений.")
    public EventingMaintenanceReport previewMaintenance(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.previewMaintenance();
    }

    @Get("/export")
    @Operation(summary = "Экспорт snapshot eventing", description = "Экспорт in-memory состояния eventing для миграции/диагностики.")
    public EventingSnapshot exportSnapshot(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.exportSnapshot();
    }

    @Post("/import")
    @Operation(summary = "Импорт snapshot eventing", description = "Импортирует in-memory состояние eventing.")
    public EventingImportResult importSnapshot(HttpRequest<?> request,
                                               @Body EventingSnapshot snapshot,
                                               @QueryValue(defaultValue = "false") boolean clearBeforeImport) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.importSnapshot(snapshot, clearBeforeImport);
    }

    @Post("/import/preview")
    @Operation(summary = "Предпросмотр импорта snapshot", description = "Показывает итоги будущего импорта без изменения состояния.")
    public EventingImportResult previewImportSnapshot(HttpRequest<?> request,
                                                      @Body EventingSnapshot snapshot,
                                                      @QueryValue(defaultValue = "false") boolean clearBeforeImport,
                                                      @QueryValue(defaultValue = "true") boolean strictPolicies) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.previewImport(snapshot, clearBeforeImport, strictPolicies);
    }

    @Post("/import/analyze")
    @Operation(summary = "Расширенный анализ snapshot import", description = "Возвращает dry-run анализ import (валидность, прогноз размеров, переполнения лимитов).")
    public EventingImportAnalysis analyzeImportSnapshot(HttpRequest<?> request,
                                                        @Body EventingSnapshot snapshot,
                                                        @QueryValue(defaultValue = "false") boolean clearBeforeImport,
                                                        @QueryValue(defaultValue = "true") boolean strictPolicies) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.analyzeImport(snapshot, clearBeforeImport, strictPolicies);
    }

    @Post("/import/validate")
    @Operation(summary = "Валидация snapshot", description = "Проверяет корректность snapshot и возвращает список нарушений без изменения состояния.")
    public EventingSnapshotValidation validateSnapshot(HttpRequest<?> request,
                                                       @Body EventingSnapshot snapshot,
                                                       @QueryValue(defaultValue = "true") boolean strictPolicies) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.validateSnapshot(snapshot, strictPolicies);
    }

    @Get("/capabilities")
    @Operation(summary = "Capabilities eventing API", description = "Список доступных возможностей текущего eventing-инкремента.")
    public EventingCapabilities capabilities(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.capabilities();
    }

    @Get("/limits")
    @Operation(summary = "Лимиты eventing", description = "Текущие лимиты и политики import-governance.")
    public EventingLimits limits(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return dispatcherService.limits();
    }

    @Get("/version")
    @Operation(summary = "Версия eventing stage", description = "Текущий stage eventing-функционала.")
    public Map<String, String> version(HttpRequest<?> request) {
        var subject = RequestSecurityContext.current(request)
                .orElseThrow(() -> new UnauthorizedException("Субъект не аутентифицирован"));
        authorizationService.requirePermission(subject, "event-process");
        return Map.of("stage", "6.12", "label", "snapshot analysis + violation codes");
    }

    private List<IntegrationEvent> filterEvents(Stream<IntegrationEvent> stream,
                                                String eventType,
                                                String source,
                                                int limit,
                                                int offset,
                                                String from,
                                                String to,
                                                boolean desc) {
        if (limit <= 0) {
            throw new ValidationException("limit должен быть > 0");
        }
        if (offset < 0) {
            throw new ValidationException("offset должен быть >= 0");
        }
        Instant fromTs = parseInstantOrNull(from, "from");
        Instant toTs = parseInstantOrNull(to, "to");
        if (fromTs != null && toTs != null && fromTs.isAfter(toTs)) {
            throw new ValidationException("from должен быть раньше to");
        }
        Stream<IntegrationEvent> filtered = stream;
        if (!eventType.isBlank()) {
            filtered = filtered.filter(e -> eventType.equals(e.eventType()));
        }
        if (!source.isBlank()) {
            filtered = filtered.filter(e -> source.equals(e.source()));
        }
        if (fromTs != null) {
            filtered = filtered.filter(e -> e.occurredAt() != null && !e.occurredAt().isBefore(fromTs));
        }
        if (toTs != null) {
            filtered = filtered.filter(e -> e.occurredAt() != null && !e.occurredAt().isAfter(toTs));
        }
        Comparator<IntegrationEvent> comparator = Comparator
                .comparing(IntegrationEvent::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(IntegrationEvent::eventId, Comparator.nullsLast(Comparator.naturalOrder()));
        if (desc) {
            comparator = comparator.reversed();
        }
        return filtered
                .sorted(comparator)
                .skip(offset)
                .limit(limit)
                .filter(Objects::nonNull)
                .toList();
    }

    private Instant parseInstantOrNull(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new ValidationException(field + " должен быть в ISO-8601 формате (пример: 2026-01-01T10:00:00Z)");
        }
    }
}
