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
```

## Ограничения
- inbox/DLQ/replay store пока in-memory.
- Kafka transport пока логирующий placeholder без реального consumer loop.
