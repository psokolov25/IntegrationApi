#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

APP_LOG="target/ui-smoke-app.log"
mkdir -p target
mkdir -p cache/ui-tools/npm cache/ui-tools/playwright

cleanup() {
  if [[ -n "${APP_PID:-}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" || true
  fi
}
trap cleanup EXIT

echo "[1/4] Запуск Integration API..."
mvn -q -DskipTests exec:java >"${APP_LOG}" 2>&1 &
APP_PID=$!

echo "[2/4] Ожидание readiness..."
for _ in {1..60}; do
  if curl -sf "http://127.0.0.1:8080/health/readiness" >/dev/null; then
    break
  fi
  sleep 1
done
curl -sf "http://127.0.0.1:8080/health/readiness" >/dev/null

echo "[3/4] Установка зависимостей UI-тестов..."
cd ui-tests
NPM_CONFIG_CACHE="$ROOT_DIR/cache/ui-tools/npm" npm install --silent
PLAYWRIGHT_BROWSERS_PATH="$ROOT_DIR/cache/ui-tools/playwright" npx playwright install chromium

echo "[4/4] Запуск playwright smoke тестов..."
PLAYWRIGHT_BROWSERS_PATH="$ROOT_DIR/cache/ui-tools/playwright" UI_BASE_URL="http://127.0.0.1:8080" npx playwright test

echo "UI smoke-тесты успешно завершены."
