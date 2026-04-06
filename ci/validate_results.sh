#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# validate: с runner — kubectl cp напрямую из пода в ci/artifacts.
#          Проверка наличия и корректности выходных файлов (CSV и т.д.).
# Переменные: KUBECONFIG или KUBE_CONFIG_B64, KUBE_NAMESPACE,
#             HTCONDOR_MANAGER_POD или HTCONDOR_MANAGER_POD_PREFIX, REMOTE_JOB_DIR, JOB_OUTPUT_FILES.
# Пример на runner: export KUBECONFIG=/etc/kubernetes/admin.conf
# -----------------------------------------------------------------------------
set -eu

REMOTE_JOB_DIR="${REMOTE_JOB_DIR:-/tmp/condor-job}"
ARTIFACTS_DIR="${CI_PROJECT_DIR:-.}/ci/artifacts"
OUTPUT_FILES="${JOB_OUTPUT_FILES:-analyze.csv model_output.csv parameter_file.csv run.job.output}"
NS="${KUBE_NAMESPACE:-default}"
POD_PREFIX="${HTCONDOR_MANAGER_POD_PREFIX:-htcondor-manager}"

echo "==> Настройка kubectl..."
if [ -n "${KUBE_CONFIG_B64:-}" ]; then
  echo "$KUBE_CONFIG_B64" | base64 -d > /tmp/kubeconfig
  export KUBECONFIG=/tmp/kubeconfig
fi
if [ -z "${KUBECONFIG:-}" ]; then
  echo "ERROR: задайте KUBECONFIG или KUBE_CONFIG_B64" >&2
  exit 1
fi

# Имя пода: задано явно или ищем по префиксу
if [ -n "${HTCONDOR_MANAGER_POD:-}" ]; then
  POD="$HTCONDOR_MANAGER_POD"
else
  POD=$(kubectl get pods -n "$NS" --no-headers -o custom-columns=":metadata.name" 2>/dev/null | grep "^${POD_PREFIX}" | head -1)
  if [ -z "$POD" ]; then
    echo "ERROR: под с префиксом '$POD_PREFIX' в namespace $NS не найден" >&2
    exit 1
  fi
  echo "==> Найден под по префиксу: $POD"
fi

mkdir -p "$ARTIFACTS_DIR"
echo "==> Скачивание результатов из пода $POD ($NS):$REMOTE_JOB_DIR..."
for f in $OUTPUT_FILES; do
  name=$(basename "$f")
  kubectl cp "$NS/$POD:$REMOTE_JOB_DIR/$name" "$ARTIFACTS_DIR/$name" 2>/dev/null || true
done
kubectl cp "$NS/$POD:$REMOTE_JOB_DIR/run.job.err" "$ARTIFACTS_DIR/run.job.err" 2>/dev/null || true

echo "==> Проверка выходных файлов..."
failed=0
for f in $OUTPUT_FILES; do
  path="$ARTIFACTS_DIR/$(basename "$f")"
  if [ -f "$path" ]; then
    size=$(stat -c%s "$path" 2>/dev/null || stat -f%z "$path" 2>/dev/null)
    if [ "$size" -gt 0 ]; then
      echo "  OK: $f (size $size)"
    else
      echo "  FAIL: $f пустой" >&2
      failed=1
    fi
  else
    echo "  FAIL: $f отсутствует" >&2
    failed=1
  fi
done

echo "==> Проверка структуры CSV (analyze.csv / model_output.csv)..."
for csv in analyze.csv model_output.csv; do
  p="$ARTIFACTS_DIR/$csv"
  if [ -f "$p" ]; then
    if [ "$(wc -l < "$p")" -lt 1 ]; then
      echo "  FAIL: $csv пустой или без строк" >&2
      failed=1
    else
      echo "  OK: $csv — строк: $(wc -l < "$p")"
    fi
  fi
done

if [ $failed -eq 1 ]; then
  echo "ERROR: валидация не пройдена" >&2
  exit 1
fi
echo "==> Валидация пройдена."
