# CUSTOM CLIENT FRAMEWORK

## Текущая реализация (этап 5, базовый инкремент)

Реализована базовая клиентская политика поверх `VisitManagerClient`:
- `ClientExecutionTemplate`:
  - retry;
  - timeout;
  - circuit breaker.
- `PolicyAwareVisitManagerClient` — primary-обертка над downstream клиентом.

## Конфигурация
```yaml
integration:
  client-policy:
    retry-attempts: 2
    timeout-millis: 1000
    circuit-failure-threshold: 3
    circuit-open-seconds: 10
```

## Ограничения
- Текущая версия использует in-memory состояние circuit breaker.
- Пока покрыт only VisitManager client path.
