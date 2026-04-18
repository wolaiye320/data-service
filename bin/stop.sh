#!/usr/bin/env bash
# data-service 停止脚本
# 用法: ./bin/stop.sh [--backend-only]

set -euo pipefail

BACKEND_PORT=8081
FRONTEND_PORT=3001
BACKEND_ONLY=false
FRONTEND_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --backend-only) BACKEND_ONLY=true ;;
    --frontend-only) FRONTEND_ONLY=true ;;
    -h|--help)
      echo "用法: $0 [--backend-only|--frontend-only]"
      echo "  停止 data-service 后端(8081) 与前端(3001)"
      exit 0
      ;;
    *)
      echo "未知参数: $arg"
      exit 1
      ;;
  esac
done

if [[ "$BACKEND_ONLY" == "true" && "$FRONTEND_ONLY" == "true" ]]; then
  echo "--backend-only 与 --frontend-only 不能同时使用"
  exit 1
fi

kill_port() {
  local port=$1
  local name=$2
  local pids
  pids="$(lsof -ti ":$port" 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    echo "$pids" | xargs kill -9 2>/dev/null || true
    echo "[$name] 已停止 (端口 $port)"
  else
    echo "[$name] 未运行 (端口 $port)"
  fi
}

echo "=== data-service 停止 ==="
if [[ "$FRONTEND_ONLY" != "true" ]]; then
  kill_port "$BACKEND_PORT" "后端"
fi
if [[ "$BACKEND_ONLY" != "true" ]]; then
  kill_port "$FRONTEND_PORT" "前端"
fi
echo "=== 停止完成 ==="
