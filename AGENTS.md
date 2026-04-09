# Правила для дальнейшей разработки

1. Сохранять поэтапную поставку: каждый этап должен быть рабочим и тестируемым.
2. Документация и JavaDoc/Swagger-описания — на русском языке.
3. Не логировать чувствительные секреты.
4. После каждого значимого инкремента запускать `mvn test`.
5. Для новых интеграций применять интерфейсы расширения, не ломая stage-1 API.
6. Для интеграции с VisitManager ориентироваться на dev-ветку (`psokolov25/VisitManager`) и сверять:
   - REST-контракты по `openapi.yml`;
   - структуру payload событий DataBus (`VISIT_*`, branch-state и вложенные `meta/data/...` поля);
   - модели `Branch`/`Visit` при изменении мапперов.
7. При изменении event-driven синхронизации branch-state обязательно:
   - добавлять/обновлять unit-тесты на маппинг payload;
   - проверять поведение debounce/out-of-order;
   - отражать новые параметры в `RUNBOOK.md`.
8. Итерационный цикл развития фич:
   - сначала делать широкий охват: реализовать базовый каркас по всем ключевым группам фич (без «переполировки» одной зоны);
   - после покрытия основы — переходить к углублению: в каждой следующей итерации брать отдельную группу фич и дорабатывать детально;
   - когда углубление прошло по всем группам — повторять цикл (снова широкий проход, затем углубление по группам).

## Инструменты тестирования/отладки GUI и эмуляции браузера (актуальный набор)

### Рекомендуемые основные инструменты
1. **Playwright** (основной E2E инструмент по умолчанию):
   - стабильный запуск Chromium/Firefox/WebKit;
   - перехват ошибок консоли (`page.on('console')`), сети и trace;
   - удобен для smoke/regression в CI.
2. **Cypress**:
   - быстрый DX для компонентных и E2E тестов;
   - удобный интерактивный runner для локальной отладки.
3. **Puppeteer**:
   - легковесная автоматизация Chrome/Chromium через DevTools Protocol;
   - подходит для кастомных сценариев диагностики.
4. **Selenium WebDriver**:
   - кросс-языковой стандарт и широкая экосистема grid/infra.
5. **Lighthouse + DevTools**:
   - профилирование перфоманса/доступности и аудит web quality.

### Инструменты, подтвержденно применимые в текущем Codex workflow
- `Playwright` (через `ui-tests` + `scripts/run-ui-smoke.sh`), включая проверку console errors.
- `curl` для readiness/API smoke.
- `mvn test` для unit/integration слоя backend.
- При доступности browser-инструмента окружения: скриншоты UI для визуальной валидации.


## Best practices для Web GUI в Codex (web-версия)

1. В Codex web сначала выполнять **быстрый smoke** (health + загрузка `/ui/`), затем переходить к более глубоким сценариям.
2. При наличии browser-инструмента в среде обязательно фиксировать:
   - console errors (`page.on("console")`),
   - page errors (`pageerror`),
   - network failures (4xx/5xx, aborted requests),
   - trace/screenshots/video на падениях.
3. Для воспроизводимости запускать Playwright из `scripts/run-ui-smoke.sh` и сохранять артефакты в `./cache/ui-tools/*`.
4. Если Chromium недоступен в окружении, использовать fallback на Firefox/WebKit, не останавливая цикл smoke/regression.
5. Для диагностики API/GUI расхождений параллельно выполнять `curl` smoke на backend endpoints.

## Политика кэширования инструментов и зависимостей

- Любые сборочные и тестовые кэши должны храниться в `./cache` (не коммитится в git).
- Maven-репозиторий: `./cache/m2` (через `.mvn/maven.config`).
- npm cache для ui-tests: `./cache/ui-tools/npm`.
- Playwright browsers/artifacts/reports: `./cache/ui-tools/playwright*`.
- Запрещено складывать бинарные зависимости во внепроектные persistent-кэши при CI/локальном воспроизведении сценариев этого репозитория.


### Что делать при ошибках Playwright вида `libatk-1.0.so.0`/`missing dependencies`

1. Сначала запустить `scripts/run-ui-smoke.sh` — скрипт сам попытается выполнить `playwright install-deps` и `apt-get install` нужных библиотек.
2. Если автопочинка не сработала, вручную выполнить:
   - `cd ui-tests && PLAYWRIGHT_BROWSERS_PATH=../cache/ui-tools/playwright npx playwright install-deps`;
   - для Debian/Ubuntu: `apt-get install` пакеты GTK/ATK/GStreamer из playbook (ниже).
3. После установки зависимостей повторить `scripts/run-ui-smoke.sh`.
4. Если Chromium все еще недоступен — использовать fallback-проект `UI_SMOKE_PROJECT=firefox-desktop` или `webkit-desktop`.


## Алгоритм восстановления GUI-инструментария (если всё отсутствует)

1. **Проверить базовые зависимости окружения**:
   - `java -version` (JDK 17+), `mvn -v`, `node -v`, `npm -v`, `curl --version`.
2. **Подготовить кэш-директории** (только внутри проекта):
   - `mkdir -p cache/m2 cache/ui-tools/npm cache/ui-tools/playwright cache/ui-tools/playwright-artifacts cache/ui-tools/playwright-report`.
3. **Восстановить backend build toolchain**:
   - использовать `.mvn/maven.config` (`-Dmaven.repo.local=./cache/m2`),
   - выполнить `mvn test` (подтягивает Maven-артефакты в `./cache/m2`).
4. **Восстановить Node/Playwright зависимости**:
   - `cd ui-tests && NPM_CONFIG_CACHE=../cache/ui-tools/npm npm install`;
   - `PLAYWRIGHT_BROWSERS_PATH=../cache/ui-tools/playwright npx playwright install chromium`.
5. **Восстановить системные библиотеки браузеров Linux**:
   - сначала `PLAYWRIGHT_BROWSERS_PATH=../cache/ui-tools/playwright npx playwright install-deps`;
   - при неуспехе — `apt-get install` пакетный набор из `PLAYBOOKS.md`.
6. **Проверить smoke end-to-end**:
   - запуск `./scripts/run-ui-smoke.sh`;
   - при недоступности Chromium использовать `UI_SMOKE_PROJECT=firefox-desktop` или `webkit-desktop`.
7. **Проверить артефакты и отчёты**:
   - `cache/ui-tools/playwright-artifacts` (trace/video/screenshots),
   - `cache/ui-tools/playwright-report` (HTML report),
   - `target/ui-smoke-app.log` (backend лог).
