#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# setup-nodes: проверка доступности submit- и execute-узлов (ping + SSH).
# Переменные: SUBMIT_NODE, EXECUTE_NODES (через запятую), SSH_USER, SSH_PRIVATE_KEY.
# -----------------------------------------------------------------------------
set -euo pipefail

TIMEOUT="${NODE_CHECK_TIMEOUT:-60}"
SSH_OPTS="-o StrictHostKeyChecking=no -o ConnectTimeout=10 -o BatchMode=yes"

echo "==> Настройка SSH..."
mkdir -p ~/.ssh
echo "$SSH_PRIVATE_KEY" > ~/.ssh/id_rsa
chmod 600 ~/.ssh/id_rsa

check_node() {
  local node="$1"
  echo "==> Проверка узла: $node"
  if ! ping -c 2 -W 5 "$node" 2>/dev/null; then
    echo "ERROR: ping $node failed" >&2
    return 1
  fi
  if ! ssh $SSH_OPTS "${SSH_USER}@${node}" "echo OK"; then
    echo "ERROR: SSH to $node failed" >&2
    return 1
  fi
  echo "OK: $node доступен"
}

check_node "$SUBMIT_NODE"

if [ -n "${EXECUTE_NODES:-}" ]; then
  IFS=',' read -ra NODES <<< "$EXECUTE_NODES"
  for n in "${NODES[@]}"; do
    n=$(echo "$n" | tr -d ' ')
    [ -z "$n" ] && continue
    check_node "$n"
  done
fi

echo "==> Все узлы доступны."
