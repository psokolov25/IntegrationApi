# RUNBOOK

## Playbook (операционный порядок)
1. Проверить доступность сервиса (`/health`, `/health/readiness`).
2. Проверить конфигурацию интеграции с VisitManager:
   - `integration.visit-managers[*].base-url`;
   - `integration.branch-routing` и `integration.branch-fallback-routing`;
   - `integration.branch-state-cache-ttl`, `integration.branch-state-event-refresh-debounce`.
3. Проверить branch-state API (`/api/v1/branches/*`) и eventing pipeline (`/api/v1/events/*`).
4. При проблемах консистентности branch-state:
   - проверить корректность DataBus payload;
   - проверить маппинг `VisitManagerBranchStateEventMapper`;
   - убедиться, что debounce не подавляет нужные refresh.
5. После любых изменений в коде integration/eventing — запуск `mvn test`.

## Базовые проверки
- Health: `GET /health`
- Liveness: `GET /health/liveness`
- Readiness: `GET /health/readiness`
- Gateway API: `/api/v1/queues`, `/api/v1/queues/aggregate`
- Branch-state API:
  - `GET /api/v1/branches/{branchId}/state`
  - `POST /api/v1/branches/{branchId}/state/refresh`
  - `PUT /api/v1/branches/{branchId}/state`
  - `GET /api/v1/branches/states`
- Programmable: `POST /api/v1/program/{endpointId}`
- Eventing ingestion: `POST /api/v1/events/ingest`
- Eventing replay: `POST /api/v1/events/replay/{eventId}`
- Eventing DLQ replay: `POST /api/v1/events/replay-dlq/{eventId}`
- Eventing DLQ bulk replay: `POST /api/v1/events/replay-dlq?limit=100`
- Eventing DLQ: `GET /api/v1/events/dlq`
- Eventing DLQ by id: `GET /api/v1/events/dlq/{eventId}`
- Eventing DLQ remove: `DELETE /api/v1/events/dlq/{eventId}`
- Eventing DLQ clear: `DELETE /api/v1/events/dlq`
- Eventing processed store: `GET /api/v1/events/processed`, `GET /api/v1/events/processed/{eventId}`
- Eventing processed clear: `DELETE /api/v1/events/processed`
- Eventing stats: `GET /api/v1/events/stats`
- Eventing health: `GET /api/v1/events/stats/health`
- Eventing stats reset: `DELETE /api/v1/events/stats`
- Eventing maintenance preview: `GET /api/v1/events/maintenance/preview`
- Eventing maintenance run: `POST /api/v1/events/maintenance/run`
- Eventing snapshot export: `GET /api/v1/events/export`
- Eventing snapshot import: `POST /api/v1/events/import?clearBeforeImport=false`
- Eventing snapshot import preview: `POST /api/v1/events/import/preview?clearBeforeImport=false&strictPolicies=true`
- Eventing snapshot validate: `POST /api/v1/events/import/validate?strictPolicies=true`
- Eventing snapshot analyze: `POST /api/v1/events/import/analyze?clearBeforeImport=false&strictPolicies=true`
- Eventing capabilities: `GET /api/v1/events/capabilities`
- Eventing limits: `GET /api/v1/events/limits`
- Eventing version: `GET /api/v1/events/version`

## Диагностика
- Проверить `integration.eventing.enabled` и `integration.eventing.max-retries`.
- Проверить `integration.eventing.max-payload-fields` и `integration.eventing.max-future-skew-seconds`.
- Проверить пороги `integration.eventing.dlq-warn-threshold` и `integration.eventing.duplicate-warn-threshold`.
- Проверить лимиты/retention: `integration.eventing.max-dlq-events`, `integration.eventing.max-processed-events`, `integration.eventing.retention-seconds`.
- Проверить `integration.eventing.snapshot-import-max-events` для безопасного bulk import.
- Проверить strict-политики импорта: `integration.eventing.snapshot-import-require-matching-processed-keys`,
  `integration.eventing.snapshot-import-reject-cross-list-duplicates`.
- Проверить `integration.client-policy.*` (retry/timeout/circuit).
- Проверить `integration.branch-state-cache-ttl` и `integration.branch-state-event-refresh-debounce`.
- Проверить `integration.security-mode` и permissions (`event-process` для eventing API).
- При анализе ошибок использовать поля `code/status/method/path/traceId` из `ErrorResponse`.
- Для совместимости с VisitManager сверять текущие контракты по `openapi.yml` (ветка `dev`).

## Инциденты
- DLQ растет: проверить handler для `eventType` и валидацию payload.
- Branch-state «застывает»: проверить, что приходят события `branch-state-updated`/`VISIT_*`, и нет ли слишком большого debounce-окна.
- Branch-state «скачет назад»: проверить out-of-order события и `updatedAt` в payload.
- Для точечного восстановления обработать событие через `POST /api/v1/events/replay-dlq/{eventId}`.
- Для массового восстановления использовать `POST /api/v1/events/replay-dlq?limit=N`.
- Для ручной очистки «залипших» событий использовать `DELETE /api/v1/events/dlq/{eventId}` или `DELETE /api/v1/events/dlq`.
- Повторы событий: проверить `eventId` и inbox idempotency.
- Ошибки downstream: проверить client-policy и circuit status.
