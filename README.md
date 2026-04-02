# Integration API для VisitManager

## Назначение
Integration API — отдельный gateway/federation слой для безопасной интеграции внешних систем с одной или несколькими инсталляциями VisitManager.

## Стадии реализации

### Этап 1. Базовый gateway
- единая точка входа `/api/v1`;
- API-key авторизация;
- маршрутизация branch -> target;
- кэширование очередей (TTL);
- базовый аудит.

### Этап 2. Federation + routing
- агрегированный вызов по нескольким филиалам: `GET /api/v1/queues/aggregate`;
- partial availability и fallback;
- метрики по инсталляциям VisitManager.

### Этап 3. Pluggable security
- режимы безопасности: `API_KEY`, `INTERNAL`, `KEYCLOAK`, `HYBRID`.

### Этап 4. Programmable API
- декларативные programmable endpoints через `POST /api/v1/program/{endpointId}`.

### Этап 5. Custom client framework
- retry/timeout/circuit breaker для downstream клиентов.

### Этап 6. Event-driven integration (текущий)
- ingestion endpoint: `POST /api/v1/events/ingest`;
- replay endpoint: `POST /api/v1/events/replay/{eventId}`;
- DLQ replay endpoint: `POST /api/v1/events/replay-dlq/{eventId}`;
- DLQ bulk replay endpoint: `POST /api/v1/events/replay-dlq?limit=N`;
- ops endpoints: `/api/v1/events/dlq/*`, `/api/v1/events/processed/*`, `/api/v1/events/stats`;
- ops filters: `eventType/source/from/to/offset/limit/desc` для DLQ/processed выборок;
- maintenance endpoint: `POST /api/v1/events/maintenance/run`;
- snapshot endpoints: `GET /api/v1/events/export`, `POST /api/v1/events/import`;
- governance endpoints: `POST /api/v1/events/import/preview?clearBeforeImport=...&strictPolicies=...`, `POST /api/v1/events/import/validate?strictPolicies=...`, `POST /api/v1/events/import/analyze`, `GET /api/v1/events/capabilities`, `GET /api/v1/events/limits`, `GET /api/v1/events/version`;
- inbox/idempotency + retry/DLQ + replay store;
- transport adapter SPI и базовый Kafka/DataBus adapter;
- валидация `source/occurredAt/payload-size`, базовые in-memory метрики и health-оценка по порогам.

**Статус текущей стадии:** Этап 6.12 (snapshot analysis + violation codes) реализован, **готово к стадии тестирования**.

## Быстрый запуск
```bash
mvn test
mvn exec:java
```

Проверки health:
- `GET /health`
- `GET /health/liveness`
- `GET /health/readiness`

## Ограничения текущего состояния
- Eventing слой пока in-memory (без Kafka consumer group и persistent inbox/outbox);
- VisitManager downstream пока stub;
- production-hardening еще впереди.
- Error contract стандартизован, но пока без централизованной external observability sink.

## Следующий шаг
Этап 7: consolidation / production hardening.
