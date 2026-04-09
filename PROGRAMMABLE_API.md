# PROGRAMMABLE API

## Текущая реализация

Добавлен безопасный декларативный механизм programmable endpoints:
- endpoint вызова: `POST /api/v1/program/{endpointId}`;
- описание endpoint-ов в конфигурации `integration.programmable-api.endpoints`;
- поддерживаемые операции:
  - `FETCH_QUEUES`;
  - `AGGREGATE_QUEUES`.

Также добавлен Groovy-script runtime для интеграционных сценариев:
- `PUT /api/v1/program/scripts/{scriptId}` — сохранить скрипт;
- `GET /api/v1/program/scripts/{scriptId}` — получить скрипт;
- `DELETE /api/v1/program/scripts/{scriptId}` — удалить скрипт;
- `POST /api/v1/program/scripts/{scriptId}/execute` — выполнить скрипт.
- `POST /api/v1/program/messages/inbound` — обработать входящее сообщение брокера/шины через Groovy-реакции.

Поддерживаемые типы скриптов:
- `BRANCH_CACHE_QUERY` — доступ к актуальному branch-state кэшу IntegrationAPI;
- `VISIT_MANAGER_ACTION` — формирование/вызов REST-операций в реальный VisitManager.
- `MESSAGE_BUS_REACTION` — реакция на входящие сообщения от шин/брокеров заказчика.

Дополнительно в runtime доступны:
- `externalRestClient` — программируемый REST-клиент к внешним сервисам заказчика;
- `messageBusGateway` — единый интерфейс отправки в брокеры/шины данных заказчика.

## Безопасность
- доступ к скриптам защищается отдельными правами:
  - `programmable-script-manage` (создание/обновление/удаление);
  - `programmable-script-execute` (чтение/выполнение).

## Пример
```yaml
integration:
  programmable-api:
    enabled: true
    script-storage:
      redis:
        enabled: true
        host: localhost
        port: 6379
        database: 0
        key-prefix: integration:groovy:script:
    external-rest-services:
      - id: customer-crm
        base-url: https://crm.customer.local
        default-headers:
          X-Source-System: integration-api
    message-brokers:
      - id: customer-databus
        type: KAFKA
        enabled: true
        properties:
          topic-prefix: customer.
    message-reactions:
      - broker-id: customer-databus
        topic: branch.state.changed
        script-id: branch-state-reaction
        enabled: true
    endpoints:
      - id: queuesByBranch
        operation: FETCH_QUEUES
        required-permission: queue-view
```

### Пример `BRANCH_CACHE_QUERY` скрипта
```groovy
def branchId = input.branchId ?: 'BR-001'
def target = input.target ?: ''
def state = getBranchState.apply(branchId, target)
[
  branchId: state.branchId(),
  status: state.status(),
  queueSize: state.queueSize(),
  updatedAt: state.updatedAt().toString()
]
```

### Пример `VISIT_MANAGER_ACTION` скрипта
```groovy
def target = input.target ?: 'vm-main'
def action = input.action ?: 'call'
def path = "/api/v1/visits/" + input.visitId + "/" + action
visitManagerInvoker.invoke(target, "POST", path, [operator: input.operator], ["Content-Type":"application/json"])
```

### Пример вызова внешнего REST сервиса заказчика
```groovy
externalRestClient.invoke(
  "customer-crm",
  "POST",
  "/api/v1/branch-state/sync",
  [branchId: input.branchId, status: input.status],
  ["Content-Type":"application/json"]
)
```

### Пример отправки сообщения в шину/брокер заказчика
```groovy
messageBusGateway.publish(
  "customer-databus",
  "branch.state.changed",
  input.branchId as String,
  [branchId: input.branchId, status: input.status, updatedAt: input.updatedAt],
  ["x-source":"integration-api"]
)
```

### Пример `MESSAGE_BUS_REACTION` скрипта
```groovy
def event = input.payload
if (event.status == 'CLOSED') {
  externalRestClient.invoke(
    "customer-crm",
    "POST",
    "/api/v1/alerts/branch-closed",
    [branchId: event.branchId, sourceTopic: input.topic],
    ["Content-Type":"application/json"]
  )
}
[processed: true, brokerId: input.brokerId, topic: input.topic]
```
