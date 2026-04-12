# RUNBOOK

## Playbook (операционный порядок)
1. Проверить доступность сервиса (`/health`, `/health/readiness`).
2. Проверить конфигурацию интеграции с VisitManager:
   - `integration.visit-managers[*].base-url`;
   - `integration.branch-routing` и `integration.branch-fallback-routing`;
   - `integration.branch-state-cache-ttl`, `integration.branch-state-event-refresh-debounce`;
   - `integration.aggregate-max-branches` (лимит количества **уникальных** `branchIds` после нормализации в `GET /api/v1/queues/aggregate`);
   - `integration.aggregate-request-timeout-millis` (timeout fan-out для `GET /api/v1/queues/aggregate`);
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
- `GET /health/readiness` возвращает компоненты по ключевым группам (`gateway`, `federation`, `aggregation`, `eventing`, `security-mode`, `security`, `programmable-api`, `client-policy`, `observability`); `security` отражает корректность конфигурации режима безопасности (например, `API_KEY` без ключей -> `DOWN`, `HYBRID` без API keys и keycloak issuer -> `DEGRADED`).
- Итоговый readiness становится `DEGRADED`, если любой компонент имеет `DOWN` или `DEGRADED`.
- Gateway API: `/api/v1/queues`, `/api/v1/queues/aggregate`
- Branch-state API:
  - `GET /api/v1/branches/{branchId}/state`
  - `POST /api/v1/branches/{branchId}/state/refresh`
  - `PUT /api/v1/branches/{branchId}/state`
  - `GET /api/v1/branches/states`
- Programmable: `POST /api/v1/program/{endpointId}`
- Programmable Studio bootstrap: `GET /api/v1/program/studio/bootstrap?debugHistoryLimit=20` (для GUI/IDE редактора: runtime, inbox/outbox, connectors, settings, tabs, `editorSettings`, `editorCapabilities`, `gui.actions`).
- Programmable Studio dashboard: `GET /api/v1/program/studio/dashboard?debugHistoryLimit=20` (сводный GUI snapshot + connectors health + VisitManager metrics).
- IDE editor settings API:
  - `GET /api/v1/program/studio/settings`
  - `PUT /api/v1/program/studio/settings` (theme/fontSize/autoSave/wordWrap/lastScriptId).
  - `GET /api/v1/program/studio/settings/export` (backup настроек IDE по всем subject).
  - `POST /api/v1/program/studio/settings/import` (restore/merge настроек IDE через GUI payload).
  - `GET /api/v1/program/studio/capabilities` (доступные темы/лимиты и путь персистентности настроек).
  - `GET /api/v1/program/studio/operations/catalog` (каталог операций и templates параметров для GUI).
  - `POST /api/v1/program/studio/operations` (операции: `FLUSH_OUTBOX`, `RECOVER_STALE_INBOX`, `CLEAR_DEBUG_HISTORY`, `REFRESH_BOOTSTRAP`, `SNAPSHOT_INBOX_OUTBOX`, `SNAPSHOT_VISIT_MANAGERS`, `SNAPSHOT_BRANCH_CACHE`, `SNAPSHOT_EXTERNAL_SERVICES`, `SNAPSHOT_RUNTIME_SETTINGS`, `EXPORT_EDITOR_SETTINGS`, `PREVIEW_EVENTING_MAINTENANCE`, `EXPORT_EVENTING_SNAPSHOT`, `DASHBOARD_SNAPSHOT`).
  - `GET /api/v1/program/studio/playbook` (пошаговый операционный чек-лист по всем группам studio-фич).

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
- Для режима отладки без ключей проверить `integration.anonymous-access.enabled=true`:
  - субъект берётся из `integration.anonymous-access.subject-id`;
  - полный доступ задаётся через `integration.anonymous-access.permissions`;
  - использовать только в dev/test окружениях.
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
- Если payload содержит коллекции/неоднородную вложенность (например, `data.entities[*]`, snake_case/kebab-case ключи), маппер branch-state выполняет нормализацию ключей и рекурсивный поиск по path, поэтому при диагностике нужно проверить фактическое расположение данных, а не только «плоские» пути из примеров.
- Если `servicePoints` отсутствуют, fallback `queueSize` может вычисляться по `queues[*].visits`; при расхождении числа клиентов с UI сверять оба источника (`servicePoints` и `queues`) в snapshot.
- Для нестандартных payload можно переопределять в конфигурации `integration.eventing.entity-changed-branch-mapping`: `wrapper-keys`, `queue-snapshot-roots`, `service-points-keys`, `queues-keys`, `visits-keys` (без изменения кода).
- Для `*-paths` и `queue-snapshot-roots` поддерживается wildcard-сегмент `*` (пример: `payload.records.*.after_state.id`) для поиска по массивам/словарям с динамическими ключами.
- Branch-state «скачет назад»: проверить out-of-order события и `updatedAt` в payload.
- При одинаковом `updatedAt` кэш branch-state сохраняет уже примененное состояние (второй апдейт игнорируется), поэтому для детерминированной синхронизации источнику нужно передавать монотонный `updatedAt` на каждое изменение.
- Для `VISIT_*` убедиться, что `occurredAt` монотонно возрастает в рамках пары `visitManagerId + branchId`; более старые события должны игнорироваться.
- Для `VISIT_*` debounce/out-of-order трекинг очищает устаревшие ключи автоматически (retention = `max(1 минута, debounce * 10)`); при редких событиях по филиалу после паузы это штатное поведение и не требует ручной очистки.
- Для `VISIT_*` поддерживаются как плоские поля (`branchId`, `visitManagerId`), так и вложенные варианты (`data.branch.id`, `data.visit.branch.id`, `data.entities[*].visit.branch.id`, `data.meta.visitManagerId`, snake_case), поэтому при интеграции с DataBus проверять фактическую вложенность `meta/data/...`.
- Для `branch-state-updated`/`ENTITY_CHANGED` поле `updatedAt` можно передавать как ISO-8601, так и epoch (`seconds`/`millis`); при нестабильном формате времени на стороне источника рекомендуется унифицировать его до ISO-8601.
- Для проблем маршрутизации в внешние системы проверять аудитории `employee-workplace` и `reception-desk` (или явные `meta.targetSystems`).
- Для точечного восстановления обработать событие через `POST /api/v1/events/replay-dlq/{eventId}`.
- Для массового восстановления использовать `POST /api/v1/events/replay-dlq?limit=N`.
- Для ручной очистки «залипших» событий использовать `DELETE /api/v1/events/dlq/{eventId}` или `DELETE /api/v1/events/dlq`.
- Повторы событий: проверить `eventId` и inbox idempotency.
- Для `GET /api/v1/queues/aggregate` входной список `branchIds` нормализуется (trim пробелов, пустые/`null` отбрасываются, дубликаты удаляются с сохранением порядка первого появления).
- При `partial=true` из-за timeout проверить `integration.aggregate-request-timeout-millis` и latency downstream VisitManager.
- Если после нормализации `branchIds` не остается ни одного значения, endpoint возвращает `BAD_REQUEST` с подсказкой передать хотя бы один `branchId`.
- Ошибки downstream: проверить client-policy и circuit status.
- Ошибки выполнения Groovy-скриптов: проверить синтаксис scriptBody, тип скрипта (`BRANCH_CACHE_QUERY`/`VISIT_MANAGER_ACTION`) и доступность Redis.
- Для диагностики IDE/GUI редактора выполнять `GET /api/v1/program/studio/bootstrap` и сверять блоки `ide/runtime/connectors/eventing/settings/gui`.
- Для быстрой операторской сводки использовать `GET /api/v1/program/studio/dashboard` (включает dashboard snapshot, connectors health и метрики VisitManager).
- Для точечной диагностики использовать studio operations:
  - `SNAPSHOT_INBOX_OUTBOX` — срез backlog inbox/outbox с фильтрацией статуса;
  - `SNAPSHOT_VISIT_MANAGERS` — срез конфигурации VisitManager/маршрутизации;
  - `SNAPSHOT_BRANCH_CACHE` — срез кэша отделений (total/byVisitManager/recent, где `recent` отсортирован по `updatedAt` по убыванию, далее по `branchId` и `visitManagerId`);
  - `SNAPSHOT_EXTERNAL_SERVICES` — срез внешних REST-сервисов и message brokers;
  - `SNAPSHOT_RUNTIME_SETTINGS` — runtime-срез операционных настроек панели (eventing/aggregation/branch-cache);
  - `EXPORT_EDITOR_SETTINGS` — экспорт настроек IDE для backup;
  - `PREVIEW_EVENTING_MAINTENANCE` — dry-run очистки inbox/outbox/DLQ/processed;
  - `EXPORT_EVENTING_SNAPSHOT` — экспорт eventing snapshot для import/export сценариев;
  - `DASHBOARD_SNAPSHOT` — единый сводный snapshot GUI (workspace + inbox/outbox + VisitManagers + branch-state cache + external services + runtime settings).
- При проблемах персонализации IDE проверить `GET/PUT /api/v1/program/studio/settings` и корректность значений (`theme=dark|light|contrast`, `fontSize=10..28`).
- Для backup/restore GUI-настроек использовать `GET /api/v1/program/studio/settings/export` и `POST /api/v1/program/studio/settings/import` (`replaceExisting=true|false`).
- Для GUI миграции eventing состояния использовать `GET /api/v1/events/export`, `POST /api/v1/events/import/preview`, `POST /api/v1/events/import`.
- Настройки IDE персистятся в файл `.../editor-settings.json` внутри `integration.programmable-api.script-storage.file.path` (по умолчанию `cache/program-scripts/editor-settings.json`).
- Ошибки отправки в брокер/шину: проверить корректность `brokerId`, `topic`, тип брокера и наличие adapter-а для `message-brokers[*].type`.
- Нет реакции на входящее сообщение: проверить matching `broker-id`/`topic`, тип скрипта `MESSAGE_BUS_REACTION` и права `programmable-script-execute`.
