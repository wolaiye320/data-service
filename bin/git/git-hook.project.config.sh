#!/usr/bin/env bash

# 项目级 hook 配置。
# 个性化内容集中放在这里，脚本主体保持不变。

GIT_HOOK_BACKEND_MODULES="${GIT_HOOK_BACKEND_MODULES:-linkinsight-common linkinsight-data-mgmt linkinsight-system-mgmt linkinsight-knowledge linkinsight-query linkinsight-research linkinsight-insight linkinsight-execution linkinsight-app}"
GIT_HOOK_BACKEND_MODULE_PATTERNS="${GIT_HOOK_BACKEND_MODULE_PATTERNS:-linkinsight-*/src/main/java/* linkinsight-*/src/test/java/* linkinsight-common/* linkinsight-data-mgmt/* linkinsight-system-mgmt/* linkinsight-knowledge/* linkinsight-query/* linkinsight-research/* linkinsight-insight/* linkinsight-execution/* linkinsight-app/*}"
GIT_HOOK_BACKEND_TEST_COMMAND_TEMPLATE="${GIT_HOOK_BACKEND_TEST_COMMAND_TEMPLATE:-mvn -q -pl %MODULE% -am test}"
GIT_HOOK_ROOT_TEST_TRIGGER_PATTERNS="${GIT_HOOK_ROOT_TEST_TRIGGER_PATTERNS:-pom.xml}"
GIT_HOOK_ROOT_TEST_COMMAND="${GIT_HOOK_ROOT_TEST_COMMAND:-}"
GIT_HOOK_FRONTEND_PATH_PATTERNS="${GIT_HOOK_FRONTEND_PATH_PATTERNS:-linkinsight-web/*}"
GIT_HOOK_FRONTEND_BUILD_COMMAND="${GIT_HOOK_FRONTEND_BUILD_COMMAND:-pnpm --dir linkinsight-web build}"
GIT_HOOK_REQUIRED_TEST_CODE_PATTERNS="${GIT_HOOK_REQUIRED_TEST_CODE_PATTERNS:-linkinsight-*/src/main/java/*}"
GIT_HOOK_REQUIRED_TEST_PATH_TEMPLATE="${GIT_HOOK_REQUIRED_TEST_PATH_TEMPLATE:-%MODULE%/src/test/java/*}"
GIT_HOOK_REQUIRED_TEST_MESSAGE="${GIT_HOOK_REQUIRED_TEST_MESSAGE:-每个功能至少补充对应模块 src/test/java 下的单元测试。}"

if [[ -z "$GIT_HOOK_ROOT_TEST_COMMAND" && -n "${GIT_HOOK_BACKEND_MODULES// }" ]]; then
  GIT_HOOK_ROOT_TEST_COMMAND="mvn -q -pl $(printf '%s' "$GIT_HOOK_BACKEND_MODULES" | tr ' ' ',') -am test"
fi

if [[ -z "${GIT_HOOK_PATH_COMMANDS:-}" ]]; then
  path_commands=()
  for pattern in $GIT_HOOK_ROOT_TEST_TRIGGER_PATTERNS; do
    [[ -n "$pattern" && -n "$GIT_HOOK_ROOT_TEST_COMMAND" ]] || continue
    path_commands+=("${pattern}::${GIT_HOOK_ROOT_TEST_COMMAND}")
  done
  for pattern in $GIT_HOOK_BACKEND_MODULE_PATTERNS; do
    [[ -n "$pattern" ]] || continue
    path_commands+=("${pattern}::${GIT_HOOK_BACKEND_TEST_COMMAND_TEMPLATE}")
  done
  for pattern in $GIT_HOOK_FRONTEND_PATH_PATTERNS; do
    [[ -n "$pattern" && -n "$GIT_HOOK_FRONTEND_BUILD_COMMAND" ]] || continue
    path_commands+=("${pattern}::${GIT_HOOK_FRONTEND_BUILD_COMMAND}")
  done
  GIT_HOOK_PATH_COMMANDS="$(printf '%s\n' "${path_commands[@]}")"
fi

if [[ -z "${GIT_HOOK_REQUIRED_TEST_RULES:-}" ]]; then
  test_rules=()
  for pattern in $GIT_HOOK_REQUIRED_TEST_CODE_PATTERNS; do
    [[ -n "$pattern" ]] || continue
    test_rules+=("${pattern}::${GIT_HOOK_REQUIRED_TEST_PATH_TEMPLATE}::${GIT_HOOK_REQUIRED_TEST_MESSAGE}")
  done
  GIT_HOOK_REQUIRED_TEST_RULES="$(printf '%s\n' "${test_rules[@]}")"
fi
