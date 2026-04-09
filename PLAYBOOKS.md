# PLAYBOOKS

## Playbook: GUI Smoke + Console Validation

1. Запустить backend локально (или использовать `scripts/run-ui-smoke.sh`).
2. Выполнить Playwright smoke:
   - проверка загрузки `/ui/`;
   - проверка критичных действий в Dashboard/Control/Script Editor;
   - fail-fast при `console.error` и `pageerror`.
3. Зафиксировать артефакты:
   - trace/screenshots/video (если включено);
   - лог backend (`target/ui-smoke-app.log`).
4. Повторить с разными браузерами (Chromium как минимум, Firefox/WebKit по необходимости).

## Playbook: Отладка параметров выполнения Groovy-скриптов

1. В GUI открыть скрипт и нажать «Обновить параметры из скрипта».
2. Проверить автоматически извлечённые placeholders `{{paramName}}`.
3. Добавить недостающие параметры вручную.
4. Выполнить `Debug run` (advanced mode) с payload + parameters + context.
5. Проверить, что в ответе debug параметры отражены и бизнес-логика скрипта отработала корректно.

## Playbook: Персистентность настроек/обработчиков

1. Хранилище скриптов по умолчанию: `integration.programmable-api.script-storage.file.path=cache/program-scripts`.
2. Сохранить/изменить несколько скриптов через API или GUI.
3. Перезапустить сервис/контейнер.
4. Проверить `GET /api/v1/program/scripts` — скрипты должны восстановиться из файлового хранилища.

## Playbook: Персистентность eventing после рестарта

1. Убедиться, что `integration.eventing.state-persistence-enabled=true`.
2. Обработать несколько событий (чтобы заполнились processed/DLQ/outbox).
3. Перезапустить сервис.
4. Проверить `GET /api/v1/events/processed`, `GET /api/v1/events/dlq`, `GET /api/v1/events/outbox` — состояние должно восстановиться из `cache/eventing-state/snapshot.json`.

## Playbook: Inbox/Outbox recovery

1. Для outbox настроить `outbox-backoff-seconds` и `outbox-max-attempts`.
2. При постоянной недоступности транспорта контролировать переход outbox-записей в `DEAD`.
3. Для очистки зависших inbox processing выполнить `POST /api/v1/events/inbox/recover-stale`.
4. После восстановления выполнить `POST /api/v1/events/outbox/flush` и проверить метрики `/api/v1/events/stats`.

## Принятый стек инструментов

- Основной E2E: **Playwright**.
- Дополнительные варианты (по кейсу): **Cypress**, **Puppeteer**, **Selenium WebDriver**.
- Диагностика качества рендеринга/производительности: **Lighthouse + DevTools**.
