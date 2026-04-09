# RUNBOOK

## Playbook (операционный порядок)
1. Проверить доступность сервиса (`/health`, `/health/readiness`).
2. Проверить конфигурацию интеграции с VisitManager:
   - `integration.visit-managers[*].base-url`;
   - `integration.branch-routing` и `integration.branch-fallback-routing`;
   - `integration.branch-state-cache-ttl`, `integration.branch-state-event-refresh-debounce`;
   - `integration.eventing.entity-changed-branch-mapping.*` (eventType/class/paths для гибкого маппинга `ENTITY_CHANGED` → branch-state).
3. Проверить branch-state API (`/api/v1/branches/*`) и eventing pipeline (`/api/v1/events/*`).
4. При проблемах консистентности branch-state:
   - проверить корректность DataBus payload;
   - проверить маппинг `VisitManagerBranchStateEventMapper`;
   - убедиться, что debounce не подавляет нужные refresh.
5. После любых изменений в коде integration/eventing — запуск `mvn test`.

### Итерационный playbook развития фич
1. **Широкий проход (foundation):** покрыть базовой реализацией все ключевые группы фич (gateway, eventing, security, programmable, client-policy, observability), сохраняя рабочее состояние.
2. **Углубление по группам:** в каждой следующей итерации брать одну группу фич и усиливать детали (контракты, edge-cases, тесты, эксплуатация).
3. **Полный цикл:** когда углубление завершено по всем группам, возвращаться к новому широкому проходу и повторять цикл.
4. **Критерий готовности итерации:** функциональность группы работает end-to-end, покрыта unit/integration-тестами, а эксплуатационные шаги отражены в этом runbook.

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

- ITS (integration templates) для programmable handlers:
  - Предпросмотр: `POST /api/v1/program/templates/import/preview` (multipart `archive`).
  - Импорт: `POST /api/v1/program/templates/import` (multipart `archive` + JSON `parameterValues` + `replaceExisting`).
  - Экспорт: `POST /api/v1/program/templates/export` (выгрузка `.its`).
  - Формат placeholder в Groovy: `{{paramKey}}`; значения берутся из `parameterValues` и/или `defaultValue` в `template.yml`.
- Расширенное выполнение скриптов:
  - `POST /api/v1/program/scripts/{scriptId}/execute-advanced`
  - `POST /api/v1/program/scripts/{scriptId}/debug-advanced`
  - `parameters` передаются в Groovy binding как `params`/`parameters`, `context` доступен как `context`.
- Персистентное хранилище Groovy-скриптов:
  - `integration.programmable-api.script-storage.file.enabled=true`;
  - `integration.programmable-api.script-storage.file.path=cache/program-scripts`;
  - директория `cache/` предназначена для runtime-кэшей и бинарных инструментов (не коммитится в git).
- Персистентность eventing snapshot:
  - `integration.eventing.state-persistence-enabled=true`;
  - `integration.eventing.state-persistence-path=cache/eventing-state/snapshot.json`;
  - при рестарте сервис поднимает `processed/dlq/outbox` из сохраненного snapshot.
- Дополнительные параметры надежности:
  - `integration.eventing.outbox-backoff-seconds`;
  - `integration.eventing.outbox-max-attempts` (после превышения статус outbox -> `DEAD`);
  - `integration.eventing.inbox-processing-timeout-seconds` + endpoint `POST /api/v1/events/inbox/recover-stale`.
- Programmable Groovy scripts:
  - `PUT /api/v1/program/scripts/{scriptId}`
  - `GET /api/v1/program/scripts/{scriptId}`
  - `DELETE /api/v1/program/scripts/{scriptId}`
  - `POST /api/v1/program/scripts/{scriptId}/execute`
  - `POST /api/v1/program/messages/inbound`
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
- Eventing outbox snapshot: `GET /api/v1/events/outbox`, `GET /api/v1/events/outbox/{eventId}`
- Eventing outbox flush: `POST /api/v1/events/outbox/flush?limit=100`
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
- Проверить `integration.eventing.entity-changed-branch-mapping.event-type` и соответствие `accepted-class-names` данным VisitManager.
- При изменении payload `ENTITY_CHANGED` актуализировать lists полей `*-paths` в `entity-changed-branch-mapping` (без пересборки IntegrationAPI, если используется внешний конфиг/Groovy-скрипт конфигурации).
- Проверить `integration.security-mode` и permissions (`event-process` для eventing API).
- Для Groovy script runtime проверить права `programmable-script-manage` и `programmable-script-execute`.
- Проверить конфигурацию Redis-хранилища скриптов `integration.programmable-api.script-storage.redis.*`.
- Проверить реестр внешних REST сервисов `integration.programmable-api.external-rest-services[*]`.
- Проверить реестр брокеров/шин `integration.programmable-api.message-brokers[*]` и типы adapter-ов.
- Для `WEBHOOK_HTTP`/`HTTP_WEBHOOK` в `message-brokers[*].properties` обязательно задать:
  - `url` — endpoint webhook;
  - опционально `method` (`POST|PUT|PATCH`) и `timeoutSeconds`.
- Проверить роутинг реакций на входящие сообщения `integration.programmable-api.message-reactions[*]` (broker-id/topic/script-id).
- При анализе ошибок использовать поля `code/status/method/path/traceId` из `ErrorResponse`.
- Для совместимости с VisitManager сверять текущие контракты по `openapi.yml` (ветка `dev`).
- Для сценариев посредника (VisitManager → АРМ/приемная) проверять `meta.targetSystems` в payload и соответствие ожидаемым получателям.

## Инциденты
- DLQ растет: проверить handler для `eventType` и валидацию payload.
- Outbox растет: проверить доступность внешнего транспорта и выполнить `POST /api/v1/events/outbox/flush?limit=N`.
- Branch-state «застывает»: проверить, что приходят события `branch-state-updated`/`VISIT_*`, и нет ли слишком большого debounce-окна.
- Branch-state не обновляется по `ENTITY_CHANGED`: проверить совпадение `class-name-paths` + `accepted-class-names`, и что `branch-id/status/active-window` доступны по настроенным paths. Если в payload приходит snapshot (`oldValue/newValue`) без канонических полей branch-state, используется fallback: `branchId` из `newValue.id`, `activeWindow` из `newValue.activeWindow|resetTime`, `queueSize` из суммарного числа `servicePoints[*].visits`, `status=UNKNOWN`.
- Branch-state «скачет назад»: проверить out-of-order события и `updatedAt` в payload.
- Для `VISIT_*` убедиться, что `occurredAt` монотонно возрастает в рамках пары `visitManagerId + branchId`; более старые события должны игнорироваться.
- Для проблем маршрутизации в внешние системы проверять аудитории `employee-workplace` и `reception-desk` (или явные `meta.targetSystems`).
- Для точечного восстановления обработать событие через `POST /api/v1/events/replay-dlq/{eventId}`.
- Для массового восстановления использовать `POST /api/v1/events/replay-dlq?limit=N`.
- Для ручной очистки «залипших» событий использовать `DELETE /api/v1/events/dlq/{eventId}` или `DELETE /api/v1/events/dlq`.
- Повторы событий: проверить `eventId` и inbox idempotency.
- Ошибки downstream: проверить client-policy и circuit status.
- Ошибки выполнения Groovy-скриптов: проверить синтаксис scriptBody, тип скрипта (`BRANCH_CACHE_QUERY`/`VISIT_MANAGER_ACTION`) и доступность Redis.
- Ошибки отправки в брокер/шину: проверить корректность `brokerId`, `topic`, тип брокера и наличие adapter-а для `message-brokers[*].type`.
- Нет реакции на входящее сообщение: проверить matching `broker-id`/`topic`, тип скрипта `MESSAGE_BUS_REACTION` и права `programmable-script-execute`.
