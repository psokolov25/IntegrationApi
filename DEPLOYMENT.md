# DEPLOYMENT

## Требования
- Java 17
- Maven 3.9+
- PostgreSQL (для следующих этапов и Flyway)

## Локальное развертывание
1. Настроить `src/main/resources/application.yml`.
2. Выбрать режим безопасности:
   - `integration.security-mode: API_KEY` или
   - `integration.security-mode: INTERNAL` или
   - `integration.security-mode: KEYCLOAK` или
   - `integration.security-mode: HYBRID`.
3. Запустить:
   ```bash
   mvn exec:java
   ```

## On-prem рекомендации
- Вынос конфигурации через переменные окружения.
- Закрытие swagger/openapi в production.
- Использование reverse-proxy c TLS/mTLS.
- Централизованный сбор логов и аудитов.
- Настройка health probes:
  - liveness: `GET /health/liveness`
  - readiness: `GET /health/readiness`

- Настроить `integration.client-policy` для retry/timeout/circuit под профиль среды.
