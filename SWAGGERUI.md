# Swagger UI и OpenAPI-документация

## 1. Что доступно

- **Swagger UI** публикуется по пути: `/swagger-ui/`.
- **Сгенерированный OpenAPI YAML** публикуется по пути: `/swagger/integration-api-openapi.yml`.
- Документация формируется из аннотаций контроллеров и моделей в коде.

## 2. Теги API

- `Integration Gateway` — операции очередей, вызова посетителей, branch-state и метрик.
- `Eventing` — прием событий, replay, DLQ, snapshot-операции event pipeline.
- `Programmable API` — управление Groovy-скриптами и программируемыми endpoint-ами.
- `Internal Auth` — получение internal JWT для технических клиентов.
- `Health` — liveness/readiness/sводный health.

## 3. Ключевые сущности (schemas)

- `QueueListResponse` — список очередей отделения + признак cache-hit.
- `QueueItemDto` — единица очереди (id, название, количество ожидающих).
- `CallVisitorRequest` — команда вызова посетителя.
- `CallVisitorResponse` — результат вызова посетителя.
- `BranchStateDto` — состояние отделения (status/activeWindow/queueSize/updatedAt).
- `AggregatedQueuesResponse` — успешные и ошибочные branch-ответы с partial flag.
- `ErrorResponse` — единый формат ошибок API.
- `HealthStatusResponse` — статус health/liveness/readiness.

## 4. Варианты ответов

Swagger-аннотации в контроллерах описывают:

- `200` — успешный результат с целевой DTO;
- `400` — ошибка валидации входных параметров/тела;
- `401` — отсутствует контекст аутентификации;
- `403` — недостаточно прав (permission-based authorization);
- `404` — сущность не найдена (например, branch/event).

## 5. Безопасность в Swagger UI

Поддержаны схемы:

- `apiKeyAuth`: header `X-API-KEY`;
- `bearerAuth`: JWT Bearer-токен.

В Swagger UI можно нажать **Authorize** и заполнить одну или обе схемы в зависимости от режима безопасности (`API_KEY`, `INTERNAL`, `KEYCLOAK`, `HYBRID`).

## 6. Генерация OpenAPI YAML и PDF

```bash
./scripts/generate-openapi-pdf.sh
```

Скрипт:
1. генерирует актуальный OpenAPI YAML в `target/generated-openapi`;
2. проверяет наличие кириллицы в YAML;
3. формирует PDF `docs/openapi/integration-api-openapi.pdf`.

Можно указать свою директорию вывода:

```bash
./scripts/generate-openapi-pdf.sh docs/custom-openapi
```

## 7. Проверка русской раскладки

Критерии корректности:

- в YAML присутствуют символы кириллицы (`А-Яа-яЁё`);
- отсутствуют артефакты кодировки (например, `�`);
- русские `summary/description` из аннотаций отображаются в Swagger UI без искажений.

