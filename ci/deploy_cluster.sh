#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# deploy-cluster: загрузка MAS из GitLab Package Registry, копирование на
# submit-ноду, запуск MAS, ожидание статуса READY (clusterStatus = DEPLOY CLUSTER).
# Переменные: MAS_PACKAGE_URL (или авто из реестра текущего проекта), SUBMIT_NODE,
#             SSH_USER, SSH_PRIVATE_KEY, CONDOR_CLUSTER_PASSWORD, [INVENTORY_CONTENT], MAS_READY_TIMEOUT.
# Опционально: MAS_PACKAGE_NAME, MAS_PACKAGE_VERSION, MAS_PACKAGE_FILE — для авто-URL реестра.
# -----------------------------------------------------------------------------
set -euo pipefail

SSH_OPTS="-o StrictHostKeyChecking=no -o ConnectTimeout=30 -o BatchMode=yes"
REMOTE_DIR="${MAS_REMOTE_DIR:-/opt/mas}"
READY_STATUS="${MAS_READY_STATUS:-DEPLOY CLUSTER}"
TIMEOUT="${MAS_READY_TIMEOUT:-300}"
POLL_INTERVAL="${MAS_POLL_INTERVAL:-10}"


# Если MAS_PACKAGE_URL не задан — берём пакет из Package Registry текущего проекта (mas/1.0/mas-deploy-v1.0.zip)
if [ -z "${MAS_PACKAGE_URL:-}" ]; then
  if [ -n "${CI_API_V4_URL:-}" ] && [ -n "${CI_PROJECT_ID:-}" ]; then
    PKG_NAME="${MAS_PACKAGE_NAME:-mas}"
    PKG_VER="${MAS_PACKAGE_VERSION:-1.0}"
    PKG_FILE="${MAS_PACKAGE_FILE:-mas-deploy-v1.0.zip}"
    MAS_PACKAGE_URL="${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/packages/generic/${PKG_NAME}/${PKG_VER}/${PKG_FILE}"
    echo "==> Используется пакет из реестра проекта: ${PKG_NAME}/${PKG_VER}/${PKG_FILE}"
  else
    echo "ERROR: MAS_PACKAGE_URL не задан и не в GitLab CI (нет CI_API_V4_URL/CI_PROJECT_ID)" >&2
    exit 1
  fi
fi

echo "==> Настройка SSH..."
mkdir -p ~/.ssh
echo "$SSH_PRIVATE_KEY" > ~/.ssh/id_rsa
chmod 600 ~/.ssh/id_rsa

echo "==> Загрузка MAS из Registry..."
curl -fSL --connect-timeout 30 --retry 2 \
  ${CI_JOB_TOKEN:+--header "JOB-TOKEN: $CI_JOB_TOKEN"} \
  -o mas-deploy.zip "$MAS_PACKAGE_URL" || {
  echo "ERROR: не удалось скачать MAS" >&2
  exit 1
}

echo "==> Распаковка MAS..."
unzip -q -o mas-deploy.zip -d mas-deploy
[ -f mas-deploy/MAS-*.jar ] || [ -f mas-deploy/*.jar ] || {
  echo "ERROR: в архиве не найден JAR" >&2
  exit 1
}

mkdir -p mas-deploy/scripts
KEY_PATH_ON_NODE="${REMOTE_DIR}/scripts/.ssh_deploy_key"

if [ -n "${INVENTORY_CONTENT:-}" ]; then
  echo "$INVENTORY_CONTENT" > mas-deploy/scripts/inventory.ini
  echo "==> Используется заданный INVENTORY_CONTENT (inventory.ini)."
else
  {
    echo "# Auto-generated from SUBMIT_NODE and EXECUTE_NODES"
    echo "[central_manager]"
    echo "submit ansible_host=${SUBMIT_NODE} ansible_user=${SSH_USER} ansible_ssh_private_key_file=${KEY_PATH_ON_NODE}"
    echo ""
    echo "[execute_nodes]"
    echo "worker ansible_host=192.168.56.105 ansible_user=kuber ansible_ssh_private_key_file=${KEY_PATH_ON_NODE}"
  
  } > mas-deploy/scripts/inventory.ini
  # Ключ для Ansible на submit-ноде (подключение к central_manager и execute_nodes)
  echo "$SSH_PRIVATE_KEY" > mas-deploy/scripts/.ssh_deploy_key
  chmod 600 mas-deploy/scripts/.ssh_deploy_key
  echo "==> Inventory сгенерирован: central_manager=$SUBMIT_NODE, execute_nodes=${EXECUTE_NODES:- none}."
fi

echo "==> Копирование MAS на submit-ноду $SUBMIT_NODE..."
ssh $SSH_OPTS "${SSH_USER}@${SUBMIT_NODE}" "mkdir -p $REMOTE_DIR"
rsync -avz -e "ssh $SSH_OPTS" mas-deploy/ "${SSH_USER}@${SUBMIT_NODE}:${REMOTE_DIR}/" || {
  echo "WARN: rsync не найден, используем scp..."
  scp $SSH_OPTS -r mas-deploy/* "${SSH_USER}@${SUBMIT_NODE}:${REMOTE_DIR}/"
}

echo "==> Запуск MAS на submit-ноду..."
JAR=$(find mas-deploy -maxdepth 1 -name '*.jar' -type f 2>/dev/null | head -1)
JAR_BASE=${JAR:+$(basename "$JAR")}
JAR_BASE=${JAR_BASE:-MAS-1.0-SNAPSHOT.jar}
ssh $SSH_OPTS "${SSH_USER}@${SUBMIT_NODE}" "cd $REMOTE_DIR && export CONDOR_CLUSTER_PASSWORD='$CONDOR_CLUSTER_PASSWORD' && nohup java -jar $JAR_BASE > mas.log 2>&1 &"
sleep 15

echo "==> Ожидание статуса READY (clusterStatus = $READY_STATUS), таймаут ${TIMEOUT}s..."
MAS_URL="http://${SUBMIT_NODE}:8080"
elapsed=0
while [ "$elapsed" -lt "$TIMEOUT" ]; do
  if status=$(curl -sf --connect-timeout 5 "${MAS_URL}/api/status" 2>/dev/null); then
    if echo "$status" | grep -q "\"clusterStatus\":\"$READY_STATUS\""; then
      echo "==> Кластер в статусе READY."
      exit 0
    fi
  fi
  sleep "$POLL_INTERVAL"
  elapsed=$((elapsed + POLL_INTERVAL))
  echo "  ... ожидание ($elapsed/${TIMEOUT}s)"
done

echo "ERROR: таймаут ожидания READY" >&2
exit 1
