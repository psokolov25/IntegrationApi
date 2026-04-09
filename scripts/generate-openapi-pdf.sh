#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

OUTPUT_DIR="${1:-docs/openapi}"
mkdir -p "$OUTPUT_DIR"

echo "[1/3] Генерация OpenAPI YAML..."
mvn -q -DskipTests compile

OPENAPI_YML="$(find target/generated-openapi -maxdepth 2 -name '*.yml' 2>/dev/null | head -n 1 || true)"
if [[ -z "${OPENAPI_YML}" && -f "target/generated-openapi/integration-api-openapi.yml" ]]; then
  OPENAPI_YML="target/generated-openapi/integration-api-openapi.yml"
fi
if [[ -z "${OPENAPI_YML}" && -f "integration-api-openapi.yml" ]]; then
  OPENAPI_YML="integration-api-openapi.yml"
fi
if [[ -z "${OPENAPI_YML}" ]]; then
  echo "Ошибка: не найден сгенерированный OpenAPI YAML в target/generated-openapi."
  exit 1
fi

echo "[2/3] Проверка кириллицы в ${OPENAPI_YML}..."
if python3 - <<'PY' "${OPENAPI_YML}"
import pathlib
import re
import sys
text = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
has_ru = bool(re.search(r"[А-Яа-яЁё]", text))
sys.exit(0 if has_ru else 1)
PY
then
  echo "OK: кириллица присутствует в YAML."
else
  echo "Ошибка: кириллица не обнаружена в YAML."
  exit 1
fi

PDF_PATH="${OUTPUT_DIR}/integration-api-openapi.pdf"
echo "[3/3] Генерация PDF ${PDF_PATH}..."
mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" \
  ru.aritmos.integration.docs.OpenApiPdfGenerator \
  "${OPENAPI_YML}" \
  "${PDF_PATH}"

echo "Готово:"
echo "  YAML: ${OPENAPI_YML}"
echo "  PDF:  ${PDF_PATH}"
