# EVENTING

## Текущая реализация (этап 6.12, snapshot analysis + violation codes)

Добавлен event-driven pipeline:
- `POST /api/v1/events/ingest`
- `POST /api/v1/events/replay/{eventId}`
- `POST /api/v1/events/replay-dlq/{eventId}`
- `POST /api/v1/events/replay-dlq?limit=100`
- `GET /api/v1/events/dlq`
- `GET /api/v1/events/dlq/{eventId}`
- `DELETE /api/v1/events/dlq/{eventId}`
- `DELETE /api/v1/events/dlq`
- `GET /api/v1/events/processed`
- `GET /api/v1/events/processed/{eventId}`
- `DELETE /api/v1/events/processed`
- `GET /api/v1/events/stats`
- `GET /api/v1/events/stats/health`
- `DELETE /api/v1/events/stats`
- `GET /api/v1/events/maintenance/preview`
- `POST /api/v1/events/maintenance/run`
- `GET /api/v1/events/export`
- `POST /api/v1/events/import?clearBeforeImport=false`
- `POST /api/v1/events/import/preview?clearBeforeImport=false&strictPolicies=true`
- `POST /api/v1/events/import/validate?strictPolicies=true`
- `POST /api/v1/events/import/analyze?clearBeforeImport=false&strictPolicies=true`
- `GET /api/v1/events/capabilities`
- `GET /api/v1/events/limits`
- `GET /api/v1/events/version`

Технические компоненты:
- inbox/idempotency (`EventInboxService`)
- retry + DLQ (`EventRetryService`)
- replay store (`EventStoreService`)
- transport adapter SPI (`EventTransportAdapter`)
- базовый Kafka/DataBus adapter (`KafkaDataBusTransportAdapter`, logging placeholder)
- HTTP webhook transport adapter (`HttpWebhookEventTransportAdapter`) для внешних шин/шлюзов
- композитный transport adapter (`CompositeEventTransportAdapter`) для параллельной публикации в активные каналы
- поддержка `ENTITY_CHANGED` для `Branch` через конфигурируемый mapping paths (`integration.eventing.entity-changed-branch-mapping.*`)
- повторная обработка событий из DLQ с удалением после успешного replay
- bulk replay DLQ с ограничением по `limit`
- фильтрация/пагинация списков по `eventType/source/from/to/offset/limit/desc`
- in-memory метрики eventing pipeline (`EventingStats`)
- оценка здоровья eventing по порогам (`EventingHealth`)
- maintenance-процедуры prune/trim (`EventingMaintenanceReport`)
- snapshot export/import (`EventingSnapshot`, `EventingImportResult`)
- import-governance (preview/analyze projection + coded violations + strict/lenient validation + capabilities/limits)

## Конфигурация
```yaml
integration:
  eventing:
    enabled: true
    outbox-backoff-seconds: 5
    outbox-max-attempts: 20
    outbox-auto-flush-enabled: false
    outbox-auto-flush-batch-size: 100
    outbox-auto-flush-interval: 30s
    outbox-auto-flush-initial-delay: 10s
    max-retries: 2
    max-payload-fields: 100
    max-future-skew-seconds: 300
    dlq-warn-threshold: 10
    duplicate-warn-threshold: 50
    max-dlq-events: 5000
    max-processed-events: 20000
    retention-seconds: 604800
    snapshot-import-max-events: 50000
    snapshot-import-require-matching-processed-keys: true
    snapshot-import-reject-cross-list-duplicates: true
    kafka:
      enabled: false
      inbound-topic: integration.inbound
      outbound-topic: integration.outbound
    webhook:
      enabled: false
      url: "https://databus-gateway.local/events"
      auth-header: "Authorization"
      auth-token: "Bearer ${TOKEN}"
      target-systems: ["employee-workplace", "reception-desk"]
      connect-timeout-millis: 1000
      read-timeout-millis: 3000
      headers:
        X-Source-System: "integration-api"
    entity-changed-branch-mapping:
      enabled: true
      event-type: ENTITY_CHANGED
      class-name-paths: ["class", "data.class", "data.entityClass"]
      accepted-class-names: ["Branch"]
      branch-id-paths: ["data.entity.id", "data.branch.id", "branchId"]
      status-paths: ["data.entity.status", "data.state.status", "status"]
      active-window-paths: ["data.entity.activeWindow", "data.state.activeWindow", "activeWindow"]
      queue-size-paths: ["data.entity.queueSize", "data.state.queueSize", "queueSize"]
      updated-at-paths: ["data.entity.updatedAt", "data.state.updatedAt", "updatedAt"]
      updated-by-paths: ["data.entity.updatedBy", "data.state.updatedBy", "updatedBy"]
      visit-manager-id-paths: ["meta.visitManagerId", "data.visitManagerId", "targetVisitManagerId"]
```

## Ограничения
- inbox/DLQ/replay store пока in-memory.
- Kafka transport пока логирующий placeholder без реального consumer loop.
- Для защиты от перегрузки среды на старте может автоматически снижаться `max-payload-fields` и `outbox-auto-flush-batch-size` (см. `runtime-safety` в `/health/readiness`).
