# Integration API для VisitManager

## Назначение
Integration API — отдельный gateway/federation слой для безопасной интеграции внешних систем с одной или несколькими инсталляциями VisitManager.

## Стадии реализации

### Этап 1. Базовый gateway
- единая точка входа `/api`;
- API-key авторизация;
- маршрутизация branch -> target;
- кэширование очередей (TTL);
- базовый аудит.

### Этап 2. Federation + routing
- агрегированный вызов по нескольким филиалам: `GET /api/queues/aggregate`;
- partial availability и fallback;
- метрики по инсталляциям VisitManager.

### Этап 3. Pluggable security
- режимы безопасности: `API_KEY`, `INTERNAL`, `KEYCLOAK`, `HYBRID`.

### Этап 4. Programmable API
- декларативные programmable endpoints через `POST /api/program/{endpointId}`.

### Этап 5. Custom client framework
- retry/timeout/circuit breaker для downstream клиентов.

### Этап 6. Event-driven integration (текущий)
- ingestion endpoint: `POST /api/events/ingest`;
- inbound Kafka/DataBus listener для подчиненной шины СУО (`integration.eventing.kafka.enabled=true`);
- replay endpoint: `POST /api/events/replay/{eventId}`;
- DLQ replay endpoint: `POST /api/events/replay-dlq/{eventId}`;
- DLQ bulk replay endpoint: `POST /api/events/replay-dlq?limit=N`;
- ops endpoints: `/api/events/dlq/*`, `/api/events/processed/*`, `/api/events/stats`;
- outbox reliability endpoints: `GET /api/events/outbox`, `GET /api/events/outbox/{eventId}`, `POST /api/events/outbox/flush?limit=N`;
- ops filters: `eventType/source/from/to/offset/limit/desc` для DLQ/processed выборок;
- maintenance endpoint: `POST /api/events/maintenance/run`;
- snapshot endpoints: `GET /api/events/export`, `POST /api/events/import`;
- governance endpoints: `POST /api/events/import/preview?clearBeforeImport=...&strictPolicies=...`, `POST /api/events/import/validate?strictPolicies=...`, `POST /api/events/import/analyze`, `GET /api/events/capabilities`, `GET /api/events/limits`, `GET /api/events/version`;
- inbox/idempotency + retry/DLQ + replay store;
- transport adapter SPI и базовый Kafka/DataBus adapter;
- inbound consumer group для DataBus (Kafka) с обработкой сообщений через тот же ingestion pipeline (`validation -> idempotency -> retry/DLQ -> outbox`);
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
  - `POST /api/program/templates/import/preview` (multipart: `archive`)
  - `POST /api/program/templates/import` (multipart: `archive`, `parameterValues`, `replaceExisting`)
  - `POST /api/program/templates/export` (JSON -> `*.its` archive download)
- Расширенное выполнение скриптов с гарантированной передачей параметров:
  - `POST /api/program/scripts/{scriptId}/execute-advanced`
  - `POST /api/program/scripts/{scriptId}/debug-advanced`
  - Формат тела: `{ payload, parameters, context }`, параметры доступны в Groovy как `params` / `parameters`.
- Персистентность обработчиков между перезапусками:
  - по умолчанию включено файловое хранилище `integration.programmable-api.script-storage.file.path=cache/program-scripts`;
  - при пересборке/перезапуске контейнера сохраненные скрипты восстанавливаются из файлов.
- Персистентность eventing-состояния между перезапусками:
  - `integration.eventing.state-persistence-enabled=true`;
  - `integration.eventing.state-persistence-path=cache/eventing-state/snapshot.json`;
  - при старте сервис восстанавливает processed/DLQ/outbox из snapshot.
- Режимы хранилища inbox/outbox (для восстановления после пересборки контейнера):
  - `integration.eventing.storage.mode=MEMORY|FILE|REDIS`;
  - для `FILE`: `integration.eventing.storage.file.path=cache/eventing-storage` (рекомендуется монтировать как отдельный volume);
  - для `REDIS`: `integration.eventing.storage.redis.host/port/database/password/key-prefix` (рекомендуется Redis с персистентным volume/AOF).
  - это же storage используется для изменяемых runtime-настроек службы (операции Studio `SNAPSHOT_RUNTIME_SETTINGS` / `APPLY_RUNTIME_SETTINGS` / `RESET_RUNTIME_SETTINGS`), чтобы значения переживали пересборку контейнера.
- Расширение reliability inbox/outbox:
  - outbox поддерживает backoff/dead-state (`outbox-backoff-seconds`, `outbox-max-attempts`);
  - опциональный auto-flush outbox по расписанию (`outbox-auto-flush-enabled`, `outbox-auto-flush-batch-size`, `outbox-auto-flush-interval`);
  - endpoint восстановления зависших inbox-записей: `POST /api/events/inbox/recover-stale`.
- Внешний transport mediation для событий:
  - поддержан HTTP webhook transport (`integration.eventing.webhook.*`) для доставки outbox-событий во внешнюю шину/шлюз;
  - webhook может фильтровать публикацию по аудиториям получателей (`integration.eventing.webhook.target-systems`) на основе `meta.targetSystems`;
  - можно использовать совместно с Kafka transport (композитная публикация в оба канала).
- Кастомизируемая обработка HTTP request/response для programmable-модуля:
  - `integration.programmable-api.http-processing.*` управляет обработкой исходящих запросов (наружу) и запросов во внутрь СУО (VisitManager);
  - поддержаны direction-header, опциональная envelope-обертка тела и нормализация/preview JSON-ответов.
  - для GUI добавлены studio-операции управления HTTP processing: `EXPORT_HTTP_PROCESSING_PROFILE`, `IMPORT_HTTP_PROCESSING_PROFILE_PREVIEW`, `IMPORT_HTTP_PROCESSING_PROFILE_APPLY`, а также `PREVIEW_HTTP_PROCESSING`/`PREVIEW_HTTP_PROCESSING_MATRIX` (direction-aware preview), `PREVIEW_CONNECTOR_PROFILE` и `VALIDATE_CONNECTOR_CONFIG`.
- Расширенный каталог коннекторов внешних шин/брокеров для GUI:
  - `GET /api/program/connectors/catalog` и `GET /api/program/connectors/broker-types` возвращают `supportedBrokerProfiles` с шаблонами параметров;
  - покрыты профили Kafka/Redpanda, RabbitMQ, NATS, Pulsar, Azure Service Bus, Google Pub/Sub, AWS Kinesis, IBM MQ, Solace, MQTT и webhook.
  - studio operations поддерживают экспорт/предпросмотр/diff/применение импорта presets коннекторов: `EXPORT_CONNECTOR_PRESETS` (с metadata formatVersion/exportedAt), `IMPORT_CONNECTOR_PRESETS_PREVIEW` (валидность, дубликаты, конфликты id с текущей конфигурацией), `IMPORT_CONNECTOR_PRESETS_DIFF` (create/update/no_changes) и `IMPORT_CONNECTOR_PRESETS_APPLY` (safe apply + rollbackSnapshot после preview).
  - для комплексной миграции интеграции добавлен единый bundle workflow: `EXPORT_INTEGRATION_CONNECTOR_BUNDLE`, `IMPORT_INTEGRATION_CONNECTOR_BUNDLE_PREVIEW`, `IMPORT_INTEGRATION_CONNECTOR_BUNDLE_APPLY` (совместный перенос HTTP processing + connector presets одним пакетом).
- CRM/МИС интеграция по идентификатору клиента:
  - добавлены endpoint-и `POST /api/program/connectors/crm/identify-client`, `POST /api/program/connectors/crm/medical-services`, `POST /api/program/connectors/crm/prebooking`;
  - реализован расширяемый интерфейс `CustomerCrmIntegrationGateway` (по умолчанию через внешний REST connector), поддерживающий сценарии поиска клиента (телефон/СНИЛС/ИНН), получения доступных услуг и данных предзаписи.
- Runtime safety guard:
  - на старте сервис оценивает доступные CPU/RAM среды и при дефиците автоматически снижает рискованные лимиты (`aggregate-max-branches`, `aggregate-request-timeout-millis`, `eventing.max-payload-fields`, `eventing.outbox-auto-flush-batch-size`);
  - статус защиты отражается в `GET /health/readiness` компонентом `runtime-safety` (`UP`/`DEGRADED`).
- Базовый контур observability readiness:
  - добавлены настройки `integration.observability.enabled`, `integration.observability.external-sink-required`, `integration.observability.external-sink-url`;
  - компонент `observability` в `GET /health/readiness` переходит в `DEGRADED`, если внешний sink объявлен обязательным, но URL не задан.
- Локальный каталог кэшей/инструментария: `cache/` (не коммитится, бинарные артефакты/браузеры/кэш npm).
- Режим downstream клиента VisitManager:
  - `integration.visit-manager-client.mode=HTTP` — прямые REST-вызовы в VisitManager (`/api/v1/queues/*`, `/api/v1/branches/*/state`) и режим по умолчанию;
  - `integration.visit-manager-client.mode=STUB` — только для локальных тестов; в readiness такой режим помечается как `DOWN` и не считается рабочим для нового продукта;
  - для HTTP режима доступны параметры `read-timeout-millis`, `auth-header`, `auth-token` и path templates (`queues-path-template`, `call-path-template`, `branch-state-path-template`) для выравнивания с контрактом конкретной инсталляции VisitManager;
  - в HTTP режиме ответ branch-state считается валидным только при наличии канонических полей `branchId`, `sourceVisitManagerId`, `updatedAt` (без legacy-fallback).
  - для адаптации к будущим изменениям структуры branch-state без рекомпиляции доступен runtime-мэппинг `branch-state-response-mapping.*` (json-path для `branchId/sourceVisitManagerId/status/activeWindow/queueSize/updatedAt/updatedBy`).
  - для адаптации структуры VISIT_* событий без рекомпиляции доступен runtime-мэппинг `integration.eventing.visit-event-mapping.*` (`branch-id-paths`, `visit-manager-id-paths`, `occurred-at-paths`, `event-id-paths`).
  - опциональный live-probe доступности VM в readiness: `readiness-probe-enabled` + `readiness-probe-path` (probe использует те же `auth-header`/`auth-token`, что и основной HTTP-клиент).
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
- Eventing слой использует in-memory inbox/outbox store (Kafka/DataBus listener поддержан, persistent store — следующий шаг hardening);
- Рабочий режим downstream VisitManager для продукта — `HTTP` (режим `STUB` оставлен только для локальных тестов).
- production-hardening еще впереди.
- Централизованный external observability sink поддержан на уровне readiness-контура (обязательность/наличие URL); полноценная поставка событий в внешний sink остаётся частью production-hardening.

## Следующий шаг
Этап 7: consolidation / production hardening.
