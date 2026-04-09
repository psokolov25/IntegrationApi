#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

APP_LOG="target/ui-smoke-app.log"
mkdir -p target
mkdir -p cache/ui-tools/npm cache/ui-tools/playwright cache/m2

cleanup() {
  if [[ -n "${APP_PID:-}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" || true
  fi
}
trap cleanup EXIT

echo "[1/5] Запуск Integration API..."
mvn -q -DskipTests exec:java >"${APP_LOG}" 2>&1 &
APP_PID=$!

echo "[2/5] Ожидание readiness..."
for _ in {1..60}; do
  if curl -sf "http://127.0.0.1:8080/health/readiness" >/dev/null; then
    break
  fi
  sleep 1
done
curl -sf "http://127.0.0.1:8080/health/readiness" >/dev/null

echo "[3/5] Установка зависимостей UI-тестов..."
cd ui-tests
NPM_CONFIG_CACHE="$ROOT_DIR/cache/ui-tools/npm" npm install --silent

SELECTED_PROJECT="${UI_SMOKE_PROJECT:-chromium-desktop}"
INSTALL_BROWSER="chromium"
if [[ "$SELECTED_PROJECT" == "firefox-desktop" ]]; then
  INSTALL_BROWSER="firefox"
elif [[ "$SELECTED_PROJECT" == "webkit-desktop" ]]; then
  INSTALL_BROWSER="webkit"
fi

PLAYWRIGHT_BROWSERS_PATH="$ROOT_DIR/cache/ui-tools/playwright" npx playwright install "$INSTALL_BROWSER"

install_linux_browser_deps() {
  if [[ "$(uname -s)" != "Linux" ]]; then
    return 0
  fi

  if command -v apt-get >/dev/null 2>&1; then
    local SUDO=""
    if [[ "$(id -u)" -ne 0 ]] && command -v sudo >/dev/null 2>&1; then
      SUDO="sudo"
    fi
    echo "[4/5] Установка системных библиотек браузеров через apt-get..."
    $SUDO apt-get update || return 1
    $SUDO apt-get install -y --no-install-recommends \
      libatk1.0-0 libatk-bridge2.0-0 libcairo2 libcairo-gobject2 \
      libgdk-pixbuf-2.0-0 libgtk-3-0 libgtk-4-1 libx11-xcb1 libxcomposite1 \
      libxdamage1 libxfixes3 libxrandr2 libgbm1 libdrm2 libasound2 libpango-1.0-0 \
      libxkbcommon0 libnss3 libnspr4 libatspi2.0-0 libwayland-client0 \
      libwayland-egl1 libwayland-server0 libvulkan1 libevent-2.1-7 libopus0 \
      libwoff1 libharfbuzz-icu0 libsecret-1-0 libenchant-2-2 libhyphen0 \
      gstreamer1.0-libav gstreamer1.0-plugins-base gstreamer1.0-plugins-good \
      libgstreamer1.0-0 libgstreamer-plugins-base1.0-0 flite || return 1
    return 0
  fi

  return 1
}

if ! PLAYWRIGHT_BROWSERS_PATH="$ROOT_DIR/cache/ui-tools/playwright" npx playwright install-deps; then
  echo "[warn] playwright install-deps завершился с ошибкой."
  if ! install_linux_browser_deps; then
    echo "[warn] Не удалось автоматически установить системные зависимости браузеров."
    echo "[warn] Продолжаю выполнение с fallback-стратегией браузеров."
  fi
fi

run_project() {
  local project="$1"
  echo "[5/5] Запуск playwright smoke тестов ($project)..."
  PLAYWRIGHT_BROWSERS_PATH="$ROOT_DIR/cache/ui-tools/playwright" UI_BASE_URL="http://127.0.0.1:8080" npx playwright test --project "$project"
}

if ! run_project "$SELECTED_PROJECT"; then
  if [[ "$SELECTED_PROJECT" == "chromium-desktop" ]]; then
    echo "[warn] Chromium smoke-тесты не прошли. Пытаюсь fallback на firefox-desktop..."
    PLAYWRIGHT_BROWSERS_PATH="$ROOT_DIR/cache/ui-tools/playwright" npx playwright install firefox
    if ! run_project "firefox-desktop"; then
      echo "[warn] Firefox smoke-тесты тоже не прошли. Пытаюсь fallback на webkit-desktop..."
      PLAYWRIGHT_BROWSERS_PATH="$ROOT_DIR/cache/ui-tools/playwright" npx playwright install webkit
      run_project "webkit-desktop"
    fi
  else
    exit 1
  fi
fi

echo "UI smoke-тесты успешно завершены."
