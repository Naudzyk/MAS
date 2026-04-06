#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Переменные: KUBECONFIG или KUBE_CONFIG_B64 (на runner), KUBE_NAMESPACE,
#             HTCONDOR_MANAGER_POD или HTCONDOR_MANAGER_POD_PREFIX, JOB_PACKAGE_PATH,
#             JOB_SUBMIT_FILE, JOB_WAIT_TIMEOUT, REMOTE_JOB_DIR, PYTHON_VENV_NAME, PIP_PACKAGES.
# -----------------------------------------------------------------------------
set -eu

REMOTE_JOB_DIR="${REMOTE_JOB_DIR:-/tmp/condor-job}"
PYTHON_VENV_NAME="${PYTHON_VENV_NAME:-python_env}"
TIMEOUT="${JOB_WAIT_TIMEOUT:-3600}"
SUBMIT_FILE="${JOB_SUBMIT_FILE:-salib.dag}"
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
kubectl get pod -n "$NS" "$POD" -o name >/dev/null || {
  echo "ERROR: под $POD в namespace $NS не найден" >&2
  exit 1
}

JOB_PATH="${JOB_PACKAGE_PATH:-.}"
if [ ! -d "$JOB_PATH" ]; then
  echo "ERROR: JOB_PACKAGE_PATH не найден: $JOB_PATH" >&2
  exit 1
fi

echo "==> Копирование пакета задания напрямую в под $POD ($NS)..."
kubectl exec -n "$NS" "$POD" -- mkdir -p "$REMOTE_JOB_DIR"
tar -czf /tmp/job.tar.gz -C "$JOB_PATH" .
kubectl cp /tmp/job.tar.gz "$NS/$POD:$REMOTE_JOB_DIR/job.tar.gz"
kubectl exec -n "$NS" "$POD" -- sh -c "cd $REMOTE_JOB_DIR && tar -xzf job.tar.gz && rm -f job.tar.gz"
rm -f /tmp/job.tar.gz

# Виртуальное окружение Python в каталоге задания (в поде)
echo "==> Создание виртуального окружения $PYTHON_VENV_NAME в поде..."
kubectl exec -n "$NS" "$POD" -- bash -c "cd $REMOTE_JOB_DIR && python3 -m venv $PYTHON_VENV_NAME && . $PYTHON_VENV_NAME/bin/activate && pip install --upgrade pip -q"
if [ -f "$JOB_PATH/requirements.txt" ]; then
  echo "==> Установка зависимостей из requirements.txt..."
  kubectl cp "$JOB_PATH/requirements.txt" "$NS/$POD:$REMOTE_JOB_DIR/requirements.txt"
  kubectl exec -n "$NS" "$POD" -- bash -c "cd $REMOTE_JOB_DIR && . $PYTHON_VENV_NAME/bin/activate && pip install -r requirements.txt -q"
fi
if [ -n "${PIP_PACKAGES:-}" ]; then
  echo "==> Установка пакетов: $PIP_PACKAGES..."
  kubectl exec -n "$NS" "$POD" -- bash -c "cd $REMOTE_JOB_DIR && . $PYTHON_VENV_NAME/bin/activate && pip install $PIP_PACKAGES -q"
fi

echo "==> Отправка задания в HTCondor (в поде $POD)..."
case "$SUBMIT_FILE" in
  *.dag)
    kubectl exec -n "$NS" "$POD" -- bash -c "cd $REMOTE_JOB_DIR && condor_submit_dag -maxjobs 100 $SUBMIT_FILE" | tee submit.out
    ;;
  *)
    kubectl exec -n "$NS" "$POD" -- bash -c "cd $REMOTE_JOB_DIR && condor_submit $SUBMIT_FILE" | tee submit.out
    ;;
esac
cluster_id=$(grep "submitted to cluster" submit.out | sed -n 's/.*submitted to cluster \([0-9]*\).*/\1/p' | head -1)

if [ -z "${cluster_id:-}" ]; then
  echo "ERROR: не удалось получить cluster id из condor_submit" >&2
  exit 1
fi

echo "==> Ожидание завершения кластера $cluster_id (таймаут ${TIMEOUT}s)..."
elapsed=0
while [ "$elapsed" -lt "$TIMEOUT" ]; do
  q=$(kubectl exec -n "$NS" "$POD" -- condor_q "$cluster_id" -nobatch 2>/dev/null || true)
  if echo "$q" | grep -q "0 jobs" || [ -z "$q" ]; then
    echo "==> Задание завершено."
    exit 0
  fi
  sleep 30
  elapsed=$((elapsed + 30))
  echo "  ... в очереди ($elapsed/${TIMEOUT}s)"
done

echo "ERROR: таймаут ожидания задания" >&2
exit 1
