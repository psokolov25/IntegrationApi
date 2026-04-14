# RUNBOOK

## Playbook (операционный порядок)
1. Проверить доступность сервиса (`/health`, `/health/readiness`).
2. Проверить конфигурацию интеграции с VisitManager:
   - `integration.visit-managers[*].base-url` (для `active=true` обязательно непустое значение);
   - `integration.visit-manager-client.mode` (`HTTP` для рабочего контура; `STUB` допускается только в локальных тестах и в readiness считается `DOWN`) и для `HTTP`: `read-timeout-millis`, `auth-header`, `auth-token`, `*-path-template`, `readiness-probe-enabled`, `readiness-probe-path` (probe использует тот же auth-заголовок/токен);
   - убедиться, что branch-state downstream возвращает канонические поля `branchId`, `sourceVisitManagerId`, `updatedAt` (без fallback на стороне integration-api).
   - при изменениях структуры branch-state в VisitManager перенастроить `integration.visit-manager-client.branch-state-response-mapping.*` (без рекомпиляции сервиса).
   - при изменениях структуры VISIT_* payload перенастроить `integration.eventing.visit-event-mapping.*` (без рекомпиляции сервиса).
   - `integration.branch-routing` и `integration.branch-fallback-routing`;
   - `integration.branch-state-cache-ttl`, `integration.branch-state-event-refresh-debounce`;
   - `integration.aggregate-max-branches` (лимит количества **уникальных** `branchIds` после нормализации в `GET /api/v1/queues/aggregate`);
   - `integration.aggregate-request-timeout-millis` (timeout fan-out для `GET /api/v1/queues/aggregate`);
   - `integration.eventing.entity-changed-branch-mapping.*` (eventType/class/paths для гибкого маппинга `ENTITY_CHANGED` → branch-state).
   - при включении подчиненной шины DataBus (Kafka listener): `integration.eventing.kafka.enabled`, `integration.eventing.kafka.bootstrap-servers`, `integration.eventing.kafka.consumer-group`, `integration.eventing.kafka.auto-offset-reset`, `integration.eventing.kafka.poll-timeout-millis`, `integration.eventing.kafka.inbound-topic`.
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
- `GET /health/readiness` возвращает компоненты по ключевым группам (`gateway`, `visit-manager-client`, `federation`, `aggregation`, `eventing`, `security-mode`, `security`, `programmable-api`, `client-policy`, `observability`); `security` отражает корректность конфигурации режима безопасности (например, `API_KEY` без ключей -> `DOWN`, `HYBRID` без API keys и keycloak issuer -> `DEGRADED`).
- `GET /health/readiness` дополнительно содержит `runtime-safety`:
  - `UP` — аппаратных ограничений не потребовалось;
  - `DEGRADED` — для защиты от подвисания и перегрузки соседних служб автоматически снижены runtime-лимиты.
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
- `POST /api/v1/program/studio/operations` (операции: `FLUSH_OUTBOX`, `RECOVER_STALE_INBOX`, `CLEAR_DEBUG_HISTORY`, `REFRESH_BOOTSTRAP`, `SNAPSHOT_INBOX_OUTBOX`, `SNAPSHOT_VISIT_MANAGERS`, `SNAPSHOT_BRANCH_CACHE`, `SNAPSHOT_EXTERNAL_SERVICES`, `SNAPSHOT_RUNTIME_SETTINGS`, `EXPORT_HTTP_PROCESSING_PROFILE`, `IMPORT_HTTP_PROCESSING_PROFILE_PREVIEW`, `IMPORT_HTTP_PROCESSING_PROFILE_APPLY`, `PREVIEW_HTTP_PROCESSING`, `PREVIEW_HTTP_PROCESSING_MATRIX`, `PREVIEW_CONNECTOR_PROFILE`, `VALIDATE_CONNECTOR_CONFIG`, `EXPORT_CONNECTOR_PRESETS`, `IMPORT_CONNECTOR_PRESETS_PREVIEW`, `IMPORT_CONNECTOR_PRESETS_DIFF`, `IMPORT_CONNECTOR_PRESETS_APPLY`, `EXPORT_INTEGRATION_CONNECTOR_BUNDLE`, `IMPORT_INTEGRATION_CONNECTOR_BUNDLE_PREVIEW`, `IMPORT_INTEGRATION_CONNECTOR_BUNDLE_APPLY`, `EXPORT_EDITOR_SETTINGS`, `PREVIEW_EVENTING_MAINTENANCE`, `EXPORT_EVENTING_SNAPSHOT`, `DASHBOARD_SNAPSHOT`).
- Каталоги коннекторов/типов:
  - `GET /api/v1/program/connectors/catalog` (включая `supportedBrokerProfiles` с property templates);
  - `GET /api/v1/program/connectors/broker-types` (типы + профили для GUI форм настройки).
  - `POST /api/v1/program/connectors/crm/identify-client` (поиск клиента во внешней CRM по строке идентификатора).
  - `POST /api/v1/program/connectors/crm/medical-services` (получение перечня доступных медуслуг по идентификатору клиента).
  - `POST /api/v1/program/connectors/crm/prebooking` (получение данных о предварительной записи по идентификатору клиента).

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
  - `integration.eventing.outbox-auto-flush-enabled` + `integration.eventing.outbox-auto-flush-batch-size` + `integration.eventing.outbox-auto-flush-interval` (фоновый flush pending/failed outbox);
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
- Проверить конфигурацию Kafka/DataBus listener (`integration.eventing.kafka.*`), особенно `bootstrap-servers` и `inbound-topic` при `kafka.enabled=true`.
- Проверить `integration.eventing.max-payload-fields` и `integration.eventing.max-future-skew-seconds`.
- Проверить пороги `integration.eventing.dlq-warn-threshold` и `integration.eventing.duplicate-warn-threshold`.
- Проверить лимиты/retention: `integration.eventing.max-dlq-events`, `integration.eventing.max-processed-events`, `integration.eventing.retention-seconds`.
- Проверить конфигурацию внешнего transport webhook: `integration.eventing.webhook.enabled`,
  `integration.eventing.webhook.url`, `integration.eventing.webhook.connect-timeout-millis`,
  `integration.eventing.webhook.read-timeout-millis`, `integration.eventing.webhook.headers`,
  `integration.eventing.webhook.target-systems` (фильтр публикации по аудиториям).
- Проверить параметры авто-отправки outbox: `integration.eventing.outbox-auto-flush-enabled`,
  `integration.eventing.outbox-auto-flush-batch-size`, `integration.eventing.outbox-auto-flush-interval`,
  `integration.eventing.outbox-auto-flush-initial-delay`.
- Проверить статус `runtime-safety` в `/health/readiness` и startup-лог `RUNTIME_SAFETY_LIMITS_APPLIED`:
  - при профиле `LOW/MEDIUM` сервис может автоматически уменьшать `aggregate-max-branches`,
    `aggregate-request-timeout-millis`, `eventing.max-payload-fields`, `eventing.outbox-auto-flush-batch-size`.
- Проверить `integration.eventing.snapshot-import-max-events` для безопасного bulk import.
- Проверить strict-политики импорта: `integration.eventing.snapshot-import-require-matching-processed-keys`,
  `integration.eventing.snapshot-import-reject-cross-list-duplicates`.
- Проверить `integration.client-policy.*` (retry/timeout/circuit).
- Проверить настройки кастомного HTTP processing-модуля (`integration.programmable-api.http-processing.*`):
  - `add-direction-header`/`direction-header-name` для трассировки направления обмена (наружу/внутрь СУО);
  - `request-envelope-enabled` для обертки outbound/inbound payload;
  - `response-body-max-chars` и `parse-json-body` для безопасной обработки ответов.
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
  - опционально `method` (`POST|PUT|PATCH`, иные значения отклоняются валидацией), `timeoutSeconds` и custom headers через префикс `header.` (например, `header.Authorization=Bearer ...`).
- Проверить роутинг реакций на входящие сообщения `integration.programmable-api.message-reactions[*]` (broker-id/topic/script-id).
- При анализе ошибок использовать поля `code/status/method/path/traceId` из `ErrorResponse`.
- Для совместимости с VisitManager сверять текущие контракты по `openapi.yml` (ветка `dev`).
- Для сценариев посредника (VisitManager → АРМ/приемная) проверять `meta.targetSystems` в payload и соответствие ожидаемым получателям.

## Инциденты
- DLQ растет: проверить handler для `eventType` и валидацию payload.
- Для inbound Kafka/DataBus при невалидном JSON listener формирует synthetic событие `DATABUS_INVALID_PAYLOAD` (id `invalid:<topic>:<partition>:<offset>`) и отправляет его в общий ingestion pipeline; при росте DLQ проверять `payload.error`, `payload.rawPayloadPreview` и контрольную сумму `payload.rawPayloadHash`.
- Outbox растет: проверить доступность внешнего транспорта и выполнить `POST /api/v1/events/outbox/flush?limit=N`.
- Outbox растет при активном webhook transport: проверить HTTP-ответы внешнего шлюза (ожидается 2xx), корректность `webhook.url`, timeout и auth-header/token.
- Если webhook получает «лишние»/«чужие» события, проверить фильтрацию `integration.eventing.webhook.target-systems`
  и поле аудитории `meta.targetSystems` в payload исходного события.
- Branch-state «застывает»: проверить, что приходят события `branch-state-updated`/`VISIT_*`, и нет ли слишком большого debounce-окна.
- Branch-state не обновляется по `ENTITY_CHANGED`: проверить совпадение `class-name-paths` + `accepted-class-names`, и что `branch-id/status/active-window` доступны по настроенным paths. Если в payload приходит snapshot (`oldValue/newValue`) без канонических полей branch-state, используется fallback: `branchId` из `newValue.id`, `activeWindow` из `newValue.activeWindow|resetTime`, `queueSize` из суммарного числа `servicePoints[*].visits`, `status=UNKNOWN`.
- Для `branch-state-updated`/`ENTITY_CHANGED` по умолчанию также поддерживаются `meta.targetVisitManagerId`, `metadata.targetVisitManagerId`, `data.meta.visitManagerId|data.meta.targetVisitManagerId` и `newValue.updatedAt|oldValue.updatedAt`; при расхождении времени/источника филиала сверять эти поля в payload.
- Если payload содержит коллекции/неоднородную вложенность (например, `data.entities[*]`, snake_case/kebab-case ключи), маппер branch-state выполняет нормализацию ключей и рекурсивный поиск по path, поэтому при диагностике нужно проверить фактическое расположение данных, а не только «плоские» пути из примеров.
- Если `servicePoints` отсутствуют, fallback `queueSize` может вычисляться по `queues[*].visits`; при расхождении числа клиентов с UI сверять оба источника (`servicePoints` и `queues`) в snapshot.
- Для нестандартных payload можно переопределять в конфигурации `integration.eventing.entity-changed-branch-mapping`: `wrapper-keys`, `queue-snapshot-roots`, `service-points-keys`, `queues-keys`, `visits-keys` (без изменения кода).
- Для `*-paths` и `queue-snapshot-roots` поддерживается wildcard-сегмент `*` (пример: `payload.records.*.after_state.id`) для поиска по массивам/словарям с динамическими ключами.
- Branch-state «скачет назад»: проверить out-of-order события и `updatedAt` в payload.
- При одинаковом `updatedAt` кэш branch-state сохраняет уже примененное состояние (второй апдейт игнорируется), поэтому для детерминированной синхронизации источнику нужно передавать монотонный `updatedAt` на каждое изменение.
- Для `branch-state-updated`/`ENTITY_CHANGED` повторная доставка одного и того же `eventId` игнорируется handler-уровнем (используется `payload.eventId` fallback, затем envelope `eventId`); при диагностике дублей сверять стабильность идентификатора на стороне источника/шины.
- Для `VISIT_*` убедиться, что `occurredAt` монотонно возрастает в рамках пары `visitManagerId + branchId`; более старые события должны игнорироваться.
- Для `VISIT_*` дубликаты определяются по `eventId` (inbox idempotency + handler-трекинг): повторная доставка того же `eventId` игнорируется, даже если `occurredAt` отличается.
- Для `VISIT_*` события с одинаковым `occurredAt`, но разными `eventId`, считаются независимыми и должны обрабатываться (при условии вне debounce-окна).
- Для `VISIT_*` debounce/out-of-order трекинг очищает устаревшие ключи автоматически (retention = `max(1 минута, debounce * 10)`); при редких событиях по филиалу после паузы это штатное поведение и не требует ручной очистки.
- Для `VISIT_*` поддерживаются как плоские поля (`branchId`, `visitManagerId`), так и вложенные варианты (`data.branch.id`, `data.visit.branch.id`, `data.entities[*].visit.branch.id`, `data.meta.visitManagerId`, snake_case), поэтому при интеграции с DataBus проверять фактическую вложенность `meta/data/...`.
- Для `VISIT_*`, если envelope не содержит `eventId/occurredAt`, handler использует fallback из payload (`eventId`, `data.visit.eventId`, `visit.eventId`, `occurredAt`, `data.meta.occurredAt`, `data.visit.occurredAt`, `timestamp`) — это критично для корректного dedupe и out-of-order контроля при проксировании через внешние шины.
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
- Для сценариев нестабильных внешних API проверять `bodyPreview`/`json` в результате programmable REST-вызовов:
  - если `json=null`, ответ не распарсился как JSON (проверить `parse-json-body`);
  - если ответ обрезан, увеличить `response-body-max-chars`.
- Для dry-run проверки кастомной обработки HTTP до релиза использовать
  `POST /api/v1/program/studio/operations` с `operation=PREVIEW_HTTP_PROCESSING`
  (параметры: `direction`, `headers`, `body`, `responseStatus`, `responseBody`, `responseHeaders`; в ответе `supportedDirections`).
- Для сравнения обработки сразу в двух направлениях (`OUTBOUND_EXTERNAL` + `INBOUND_SUO`) использовать
  `operation=PREVIEW_HTTP_PROCESSING_MATRIX` (возвращает `directionPreviews[]` с request/response preview для каждого направления).
- Для backup/миграции профиля programmable HTTP processing использовать:
  - `operation=EXPORT_HTTP_PROCESSING_PROFILE` (выгрузка текущего профиля);
  - `operation=IMPORT_HTTP_PROCESSING_PROFILE_PREVIEW` (валидация candidate-профиля, включая `directionHeaderName` и `responseBodyMaxChars`);
  - `operation=IMPORT_HTTP_PROCESSING_PROFILE_APPLY` (применение профиля после preview).
- Для расширения/подбора интеграции с внешними шинами заказчика использовать
  `GET /api/v1/program/connectors/catalog` и сверять `supportedBrokerProfiles` (type/description/propertyTemplate).
- Для точечного предпросмотра конкретного типа шины использовать
  `POST /api/v1/program/studio/operations` с `operation=PREVIEW_CONNECTOR_PROFILE` и `brokerType`.
- Для синхронизации Groovy REST-клиентов с актуальным OpenAPI внешнего сервиса (например, VisitManager) использовать
  `POST /api/v1/program/studio/operations` с `operation=GENERATE_OPENAPI_REST_CLIENTS`
  (`openApiUrl` + опциональный `serviceId`), затем использовать `generated.toolkit`:
  - `connectorPresetsPreviewRequest` / `connectorPresetsApplyRequest` для регистрации REST service;
  - `scripts[*].saveScriptRequest` для пакетной загрузки скриптов в IDE/Script API.
- Для one-shot применения сгенерированного набора (REST service + scripts) использовать
  `POST /api/v1/program/studio/operations` с `operation=APPLY_OPENAPI_REST_CLIENTS_TOOLKIT`
  (`generated` из предыдущего шага + `replaceExisting=true|false`).
- Перед включением нового broker в прод-контур проверять параметры через
  `POST /api/v1/program/studio/operations` с `operation=VALIDATE_CONNECTOR_CONFIG`
  (`brokerType` + `properties`) и устранять `missingRequiredProperties` и `adapterValidationErrors`
  (например, неподдерживаемый `method`, невалидный `timeoutSeconds`).
- Для миграции/backup-конфигураций внешних коннекторов использовать:
  - `operation=EXPORT_CONNECTOR_PRESETS` (экспорт текущих REST/broker presets + profiles + metadata `formatVersion/exportedAt`);
  - `operation=IMPORT_CONNECTOR_PRESETS_PREVIEW` (dry-run проверка импортируемого набора: `valid`, `duplicateInImport`, `conflictsWithExisting`, итоговый флаг `summary.importable`).
  - `operation=IMPORT_CONNECTOR_PRESETS_DIFF` (сравнение импортируемого набора с текущими настройками: `CREATE|UPDATE|NO_CHANGES` + summary по изменениям).
  - `operation=IMPORT_CONNECTOR_PRESETS_APPLY` (применение после preview, с параметрами `replaceExisting=true|false`, `includeRollbackSnapshot=true|false`; при невалидном наборе вернется `applied=false` + preview-детали).
- Для единой миграции HTTP processing и connector presets использовать:
  - `operation=EXPORT_INTEGRATION_CONNECTOR_BUNDLE` (экспорт единого bundle с секциями `httpProcessingProfile` и `connectorPresets`).
  - `operation=IMPORT_INTEGRATION_CONNECTOR_BUNDLE_PREVIEW` (комбинированный dry-run с проверкой двух секций одновременно).
  - `operation=IMPORT_INTEGRATION_CONNECTOR_BUNDLE_APPLY` (применение bundle после успешного preview; поддерживаются `replaceExisting` и `includeRollbackSnapshot`).
- Для диагностики IDE/GUI редактора выполнять `GET /api/v1/program/studio/bootstrap` и сверять блоки `ide/runtime/connectors/eventing/settings/gui`.
- Для CRM-кейсов клиентской идентификации (телефон/СНИЛС/ИНН и т.п.) использовать:
  - `POST /api/v1/program/connectors/crm/identify-client` — поиск клиента во внешней CRM;
  - `POST /api/v1/program/connectors/crm/medical-services` — доступные медицинские услуги по найденному клиенту;
  - `POST /api/v1/program/connectors/crm/prebooking` — данные предварительной записи/предбронирования клиента.
- Для быстрой операторской сводки использовать `GET /api/v1/program/studio/dashboard` (включает dashboard snapshot, connectors health и метрики VisitManager).
- Для точечной диагностики использовать studio operations:
  - `SNAPSHOT_INBOX_OUTBOX` — срез backlog inbox/outbox с фильтрацией статуса;
  - `SNAPSHOT_VISIT_MANAGERS` — срез конфигурации VisitManager/маршрутизации;
  - `SNAPSHOT_BRANCH_CACHE` — срез кэша отделений (total/byVisitManager/recent, где `recent` отсортирован по `updatedAt` по убыванию, далее по `branchId` и `visitManagerId`);
  - `SNAPSHOT_EXTERNAL_SERVICES` — срез внешних REST-сервисов и message brokers;
  - `SNAPSHOT_RUNTIME_SETTINGS` — runtime-срез операционных настроек панели (eventing/aggregation/branch-cache/http-processing);
  - `PREVIEW_HTTP_PROCESSING` — dry-run превью кастомной обработки programmable HTTP request/response (наружу/внутрь СУО) без сетевого вызова;
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
