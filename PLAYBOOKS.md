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


## Playbook: Codex Web GUI Debug Best Practices

1. Проверить доступность backend (`/health/readiness`) и самой страницы (`/ui/`) до запуска E2E.
2. Запустить `scripts/run-ui-smoke.sh` как базовый сценарий для Codex web workflow.
3. Включить сбор артефактов Playwright (trace/screenshot/video/report) и сохранять их в `./cache/ui-tools/*`.
4. Валидировать отсутствие критичных ошибок фронта:
   - `console.error`;
   - `pageerror`;
   - failed network requests.
5. При проблемах только с Chromium:
   - выполнить fallback на `firefox-desktop` (или `webkit-desktop`);
   - зафиксировать это в отчете, но не пропускать smoke-проверку полностью.
6. После GUI-прогона выполнить API smoke через `curl`, чтобы подтвердить, что дефект не на backend уровне.

## Playbook: Кэширование инструментов сборки и тестов

1. Maven запускать с локальным репозиторием `./cache/m2` (настроено в `.mvn/maven.config`).
2. npm для `ui-tests` запускать с `NPM_CONFIG_CACHE=./cache/ui-tools/npm`.
3. Playwright browsers хранить в `PLAYWRIGHT_BROWSERS_PATH=./cache/ui-tools/playwright`.
4. Артефакты UI smoke складывать в:
   - `./cache/ui-tools/playwright-artifacts`;
   - `./cache/ui-tools/playwright-report`.
5. Проверять, что `./cache` не попадает в git (использовать `.gitignore` + `cache/.gitignore`).


## Playbook: Починка Playwright в Linux-контейнере (missing libs)

1. Запустить `scripts/run-ui-smoke.sh` — скрипт автоматически пытается:
   - `npx playwright install-deps`;
   - fallback-установку системных библиотек через `apt-get`.
2. Если окружение не позволило автопочинку, установить вручную (Debian/Ubuntu):
   - `libatk1.0-0`, `libatk-bridge2.0-0`, `libgtk-3-0`, `libgtk-4-1`, `libgdk-pixbuf-2.0-0`,
     `libx11-xcb1`, `libxcomposite1`, `libxdamage1`, `libxfixes3`, `libxrandr2`, `libgbm1`,
     `libdrm2`, `libasound2`, `libpango-1.0-0`, `libxkbcommon0`, `libnss3`, `libnspr4`,
     `libwayland-client0`, `libwayland-egl1`, `libwayland-server0`, `libvulkan1`,
     `libgstreamer1.0-0`, `libgstreamer-plugins-base1.0-0`, `gstreamer1.0-plugins-base`,
     `gstreamer1.0-plugins-good`, `gstreamer1.0-libav`, `flite`.
3. Повторить smoke:
   - `./scripts/run-ui-smoke.sh`;
   - при необходимости принудительно выбрать браузер: `UI_SMOKE_PROJECT=firefox-desktop ./scripts/run-ui-smoke.sh`.
4. Артефакты падений анализировать в `./cache/ui-tools/playwright-artifacts` и `./cache/ui-tools/playwright-report`.


## Playbook: Полное восстановление GUI test stack с нуля

> Сценарий для случая, когда в окружении отсутствуют браузеры, node-модули, системные libs и кэши.

1. **Проверка бинарей**
   - `java -version`, `mvn -v`, `node -v`, `npm -v`, `curl --version`.
2. **Инициализация локальных кэшей**
   - `mkdir -p cache/m2 cache/ui-tools/npm cache/ui-tools/playwright cache/ui-tools/playwright-artifacts cache/ui-tools/playwright-report`.
3. **Восстановление backend**
   - `mvn test` (Maven cache автоматически идет в `./cache/m2`).
4. **Восстановление ui-tests пакетов**
   - `cd ui-tests`;
   - `NPM_CONFIG_CACHE=../cache/ui-tools/npm npm install`.
5. **Восстановление браузеров Playwright**
   - `PLAYWRIGHT_BROWSERS_PATH=../cache/ui-tools/playwright npx playwright install chromium firefox webkit`.
6. **Восстановление системных библиотек Linux для headless браузеров**
   - сначала: `PLAYWRIGHT_BROWSERS_PATH=../cache/ui-tools/playwright npx playwright install-deps`;
   - если не помогло — установить вручную пакеты (см. playbook missing libs выше).
7. **Контрольный прогон smoke**
   - `cd .. && ./scripts/run-ui-smoke.sh`.
8. **Анализ результата**
   - HTML report: `cache/ui-tools/playwright-report`;
   - traces/screenshots/video: `cache/ui-tools/playwright-artifacts`;
   - backend лог: `target/ui-smoke-app.log`.
