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
- outbox reliability endpoints: `GET /api/v1/events/outbox`, `GET /api/v1/events/outbox/{eventId}`, `POST /api/v1/events/outbox/flush?limit=N`;
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

## Swagger UI и OpenAPI/PDF

- Swagger UI: `GET /swagger-ui/`
- OpenAPI YAML: `GET /swagger/integration-api-openapi.yml`
- GUI службы (дашборд/контрольная панель/редактор Groovy): `GET /ui/`

- ITS import/export programmable handlers:
  - `POST /api/v1/program/templates/import/preview` (multipart: `archive`)
  - `POST /api/v1/program/templates/import` (multipart: `archive`, `parameterValues`, `replaceExisting`)
  - `POST /api/v1/program/templates/export` (JSON -> `*.its` archive download)
- Расширенное выполнение скриптов с гарантированной передачей параметров:
  - `POST /api/v1/program/scripts/{scriptId}/execute-advanced`
  - `POST /api/v1/program/scripts/{scriptId}/debug-advanced`
  - Формат тела: `{ payload, parameters, context }`, параметры доступны в Groovy как `params` / `parameters`.
- Персистентность обработчиков между перезапусками:
  - по умолчанию включено файловое хранилище `integration.programmable-api.script-storage.file.path=cache/program-scripts`;
  - при пересборке/перезапуске контейнера сохраненные скрипты восстанавливаются из файлов.
- Персистентность eventing-состояния между перезапусками:
  - `integration.eventing.state-persistence-enabled=true`;
  - `integration.eventing.state-persistence-path=cache/eventing-state/snapshot.json`;
  - при старте сервис восстанавливает processed/DLQ/outbox из snapshot.
- Расширение reliability inbox/outbox:
  - outbox поддерживает backoff/dead-state (`outbox-backoff-seconds`, `outbox-max-attempts`);
  - endpoint восстановления зависших inbox-записей: `POST /api/v1/events/inbox/recover-stale`.
- Локальный каталог кэшей/инструментария: `cache/` (не коммитится, бинарные артефакты/браузеры/кэш npm).
- Формат ITS: ZIP-архив с расширением `.its`, содержащий `template.yml` (`template/parameters/scripts`) и `scripts/*.groovy`.
- При импорте можно получить структуру параметров из `template.yml`, заполнить значения через GUI и выполнить подстановку `{{paramKey}}` в скриптах.
- Генерация YAML + проверка кириллицы + PDF:

```bash
./scripts/generate-openapi-pdf.sh
```

Подробная документация по тегам, сущностям, вариантам ответов и security-схемам: `SWAGGERUI.md`.
Практики и скрипты тестирования GUI/эмуляции браузера: `UI_TESTING.md`.

Проверка GUI smoke-тестами (Playwright):
```bash
./scripts/run-ui-smoke.sh
```

## Ограничения текущего состояния
- Eventing слой пока in-memory (без Kafka consumer group и persistent inbox/outbox);
- VisitManager downstream пока stub;
- production-hardening еще впереди.
- Error contract стандартизован, но пока без централизованной external observability sink.

## Следующий шаг
Этап 7: consolidation / production hardening.
