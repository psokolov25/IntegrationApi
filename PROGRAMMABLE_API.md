# PROGRAMMABLE API

## Текущая реализация (этап 4, базовый инкремент)

Добавлен безопасный декларативный механизм programmable endpoints:
- endpoint вызова: `POST /api/v1/program/{endpointId}`;
- описание endpoint-ов в конфигурации `integration.programmable-api.endpoints`;
- поддерживаемые операции:
  - `FETCH_QUEUES`;
  - `AGGREGATE_QUEUES`.

## Безопасность
- произвольный `eval` не используется;
- операции выполняются только из whitelist;
- для каждого programmable endpoint задается `required-permission`.

## Пример
```yaml
integration:
  programmable-api:
    enabled: true
    endpoints:
      - id: queuesByBranch
        operation: FETCH_QUEUES
        required-permission: queue-view
```
