# Тестирование и отладка Web GUI

## Подход (best-practice для текущего этапа)

Для GUI выбран **Playwright** как основной инструмент e2e/smoke тестов и эмуляции браузера:

- проверка загрузки реального UI в Chromium;
- проверка ошибок в browser console (`page.on('console', ...)`);
- трассировка падений через встроенные `trace`-артефакты.

## Почему Playwright

1. Надежная эмуляция браузера (desktop/mobile профили, сеть, viewport).
2. Удобная диагностика (trace viewer, скриншоты/видео по необходимости).
3. Хорошая интеграция в CI/CD сценарии.

## Что реализовано в репозитории

- `ui-tests/console-smoke.spec.mjs` — smoke тесты GUI:
  - загрузка `/ui/`;
  - контроль отсутствия `console.error`;
  - базовая проверка редактора Groovy и debug payload.
- `ui-tests/screenshot.spec.mjs` — обязательный захват скриншота UI:
  - сохраняет `cache/ui-tools/playwright-artifacts/ui-console.png`;
  - используется после smoke для визуальной валидации текущего состояния GUI.
- `scripts/run-ui-smoke.sh` — автоматический прогон:
  1. запуск Integration API;
  2. ожидание readiness;
  3. установка playwright + chromium;
  4. запуск smoke-тестов;
  5. обязательный capture скриншота UI;
  6. API smoke.

## Полезные ссылки

- Playwright best practices: https://playwright.dev/docs/best-practices
- Playwright console events: https://playwright.dev/docs/api/class-page#page-on-console
- Playwright trace viewer: https://playwright.dev/docs/trace-viewer
- Playwright emulation/devices: https://playwright.dev/docs/emulation

## Следующее развитие

- Добавить отдельные тесты для:
  - сохранения и debug-запуска Groovy-скриптов через API-key;
  - outbox flush flow (UI -> API -> визуальное обновление);
  - визуальной регрессии (snapshot/скриншот сравнения);
  - интеграции с консольными логами браузера и network error budget.
