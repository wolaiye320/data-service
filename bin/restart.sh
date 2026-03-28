#!/usr/bin/env bash
# data-service 重启脚本
# 用法: ./bin/restart.sh [--local|--dev|--prod] [--backend-only]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== data-service 重启 ==="
"$SCRIPT_DIR/stop.sh" "$@"
sleep 2
"$SCRIPT_DIR/start.sh" "$@"
