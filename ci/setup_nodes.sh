#!/bin/sh
# -----------------------------------------------------------------------------
# setup-nodes: проверка доступности submit- и execute-узлов (ping + SSH).
# Переменные: SUBMIT_NODE, EXECUTE_NODES, SSH_USER.
# Ключ всегда берём из файла на Jenkins-сервере: ~/.ssh/ci_ssh_key
# -----------------------------------------------------------------------------
set -eu
TIMEOUT="${NODE_CHECK_TIMEOUT:-60}"

SCRIPT_DIR="$(CDPATH= cd "$(dirname "$0")" && pwd)"

echo "==> Настройка SSH..."
KEY_FILE="${HOME}/.ssh/ci_ssh_key"
if [ ! -f "$KEY_FILE" ]; then
  echo "ERROR: SSH key not found at '$KEY_FILE'. Put your private key on Jenkins server." >&2
  exit 1
fi

chmod 600 "$KEY_FILE" || true
if ! ssh-keygen -y -f "$KEY_FILE" >/dev/null 2>&1; then
  echo "ERROR: existing SSH key file is not readable/valid by ssh-keygen: '$KEY_FILE'." >&2
  exit 1
fi

check_node() {
  node="$1"
  echo "==> Проверка узла: $node"
  if ! ping -c 2 -W 5 "$node" 2>/dev/null; then
    echo "ERROR: ping $node failed" >&2
    return 1
  fi
  if ! ssh -i "$KEY_FILE" \
    -o StrictHostKeyChecking=no \
    -o ConnectTimeout=10 \
    -o BatchMode=yes \
    "${SSH_USER}@${node}" "echo OK"; then
    echo "ERROR: SSH to $node failed" >&2
    return 1
  fi
  echo "OK: $node доступен"
}

check_node "$SUBMIT_NODE"

if [ -n "${EXECUTE_NODES:-}" ]; then
  OLD_IFS=$IFS
  IFS=,
  for n in $EXECUTE_NODES; do
    n=$(echo "$n" | tr -d ' ')
    [ -z "$n" ] && continue
    check_node "$n"
  done
  IFS=$OLD_IFS
fi

echo "==> Все узлы доступны."
