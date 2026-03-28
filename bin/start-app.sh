#!/bin/bash
# data-service 应用启动脚本

set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> 清理 data-service-app 构建产物..."
mvn -pl data-service-app -am clean -q

echo "==> 编译并打包 data-service-app..."
mvn -pl data-service-app -am package -DskipTests -q

jar_path="$(find data-service-app/target -maxdepth 1 -type f -name 'data-service-app-*.jar' | sort | head -n 1)"
if [[ -z "$jar_path" ]]; then
  echo "未找到可执行 JAR：data-service-app/target/data-service-app-*.jar"
  exit 1
fi

echo "==> 启动 data-service-app（local 模式）..."
java -jar "$jar_path" \
  --spring.profiles.active=local \
  "$@"
