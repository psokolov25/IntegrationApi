# SECURITY

## Этап 1-2
- Базовая аутентификация через `X-Api-Key`.
- Единая точка контроля доступа через фильтр.

## Этап 3 (текущий инкремент)
- Поддержаны режимы:
  - `API_KEY`;
  - `INTERNAL`;
  - `KEYCLOAK`;
  - `HYBRID`.
- Добавлены абстракции:
  - `AuthenticationService`;
  - `TokenService`;
  - `AuthorizationService`.
- Реализован internal token endpoint: `POST /api/v1/auth/token`.
- Реализован Keycloak JWT integration layer (dev HS256).
- Реализовано локальное дообогащение прав в hybrid mode.
- Введены permission-проверки на API:
  - `queue-view`, `queue-call`, `queue-aggregate`, `metrics-view`.
- Стандартизованы коды ошибок безопасности:
  - `401 UNAUTHORIZED` для неаутентифицированного запроса;
  - `403 FORBIDDEN` для запроса без требуемых прав.

## Дорожная карта
- Полноценная проверка JWK/RS256 и интроспекция Keycloak.
- Расширенная RBAC/ABAC policy model.
