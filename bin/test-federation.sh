#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE_DIR="${ROOT_DIR}/data-service-federation"

cd "${ROOT_DIR}"

echo "[federation] 清理模块构建产物"
rm -rf "${MODULE_DIR}/target"

echo "[federation] 执行联邦模块干净测试"
mvn -pl data-service-federation -am test \
  -Dtest=PredefinedJoinQueryExecutorTest,FederatedPipelineTest \
  -Dmaven.compiler.useIncrementalCompilation=false \
  -DfailIfNoTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false
