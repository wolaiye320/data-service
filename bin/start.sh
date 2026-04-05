#!/usr/bin/env bash
# data-service 启动脚本
# 用法: ./bin/start.sh [--local|--dev|--prod] [--backend-only]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

APP_MODULE="data-service-app"
BACKEND_PORT=8081
MODE="local"
BACKEND_ONLY=false
FRONTEND_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --local|--dev) MODE="local" ;;
    --prod) MODE="prod" ;;
    --backend-only) BACKEND_ONLY=true ;;
    --frontend-only) FRONTEND_ONLY=true ;;
    -h|--help)
      echo "用法: $0 [--local|--dev|--prod] [--backend-only]"
      echo "  --local       本地模式（默认）：mvn spring-boot:run"
      echo "  --dev         兼容旧参数，等同 --local"
      echo "  --prod        生产模式：package 后以 java -jar 启动"
      echo "  --backend-only  仅启动后端（当前项目仅维护后端进程）"
      exit 0
      ;;
    *)
      echo "未知参数: $arg"
      exit 1
      ;;
  esac
done

mkdir -p logs

if [[ "$FRONTEND_ONLY" == "true" ]]; then
  echo "当前项目未提供可启动的前端工程，无法使用 --frontend-only"
  exit 1
fi

port_in_use() {
  lsof -ti ":$1" >/dev/null 2>&1
}

require_backend_port_free() {
  if port_in_use "$BACKEND_PORT"; then
    local pids
    pids="$(lsof -ti ":$BACKEND_PORT" 2>/dev/null | tr '\n' ' ')"
    echo "[后端] 端口 $BACKEND_PORT 已被占用: ${pids}"
    echo "请先执行 ./bin/stop.sh 或 ./bin/restart.sh"
    exit 1
  fi
}

build_prod_jar() {
  echo "[后端] 编译打包 ${APP_MODULE} ..."
  mvn -pl "$APP_MODULE" -am package -DskipTests -q
}

compile_local_modules() {
  echo "[后端] 预编译 ${APP_MODULE} 及其依赖模块 ..."
  mvn -pl "$APP_MODULE" -am compile test-compile -DskipTests -q
}

find_prod_jar() {
  find "${APP_MODULE}/target" -maxdepth 1 -type f -name "${APP_MODULE}-*.jar" | sort | head -n 1
}

start_backend() {
  require_backend_port_free

  if [[ "$MODE" == "prod" ]]; then
    build_prod_jar
    local jar_path
    jar_path="$(find_prod_jar)"
    if [[ -z "$jar_path" ]]; then
      echo "[后端] 未找到可执行 JAR：${APP_MODULE}/target/${APP_MODULE}-*.jar"
      exit 1
    fi

    nohup java -jar "$jar_path" --spring.profiles.active=prod \
      > logs/backend.log 2>&1 &
  else
    compile_local_modules
    nohup mvn -f "${APP_MODULE}/pom.xml" spring-boot:run \
      -Dspring-boot.run.profiles=local \
      -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8" \
      > logs/backend.log 2>&1 &
  fi

  echo "[后端] 启动中 (端口 $BACKEND_PORT) ..."
  for _ in $(seq 1 30); do
    if port_in_use "$BACKEND_PORT"; then
      echo "[后端] 已就绪 http://localhost:${BACKEND_PORT}"
      return 0
    fi
    sleep 1
  done

  echo "[后端] 启动超时，请查看 logs/backend.log"
  exit 1
}

echo "=== data-service 启动 (${MODE} 模式) ==="
start_backend
echo "=== 启动完成 ==="
