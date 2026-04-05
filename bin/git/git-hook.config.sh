#!/usr/bin/env bash

# Git hook 配置文件。
# 这里放通用默认值；项目差异优先放到 git-hook.project.config.sh 或环境变量。

# 对比基线。为空时按候选列表自动探测。
GIT_HOOK_BASE_REF="${GIT_HOOK_BASE_REF:-}"
GIT_HOOK_BASE_REF_CANDIDATES="${GIT_HOOK_BASE_REF_CANDIDATES:-origin/main origin/master upstream/main upstream/master}"

# commit-msg 允许的类型列表。
GIT_HOOK_COMMIT_TYPES="${GIT_HOOK_COMMIT_TYPES:-feat fix docs style refactor perf test chore revert build}"

# commit-msg scope 的正则。默认要求至少一个小写字母或数字开头。
GIT_HOOK_SCOPE_REGEX="${GIT_HOOK_SCOPE_REGEX:-[a-z0-9][a-z0-9._/-]*}"

# 是否允许 merge / revert 默认提交信息直接通过。
GIT_HOOK_ALLOW_MERGE_COMMITS="${GIT_HOOK_ALLOW_MERGE_COMMITS:-true}"
GIT_HOOK_ALLOW_REVERT_COMMITS="${GIT_HOOK_ALLOW_REVERT_COMMITS:-true}"

# pre-commit 阶段的文档命名检查脚本。为空则按默认路径查找。
GIT_HOOK_DOC_FILENAME_CHECK_SCRIPT="${GIT_HOOK_DOC_FILENAME_CHECK_SCRIPT:-}"
GIT_HOOK_DOC_FILENAME_ROOTS="${GIT_HOOK_DOC_FILENAME_ROOTS:-docs}"
GIT_HOOK_DOC_FILENAME_EXTENSIONS="${GIT_HOOK_DOC_FILENAME_EXTENSIONS:-md}"

# pre-commit 大文件阈值，单位 byte。
GIT_HOOK_MAX_FILE_SIZE_BYTES="${GIT_HOOK_MAX_FILE_SIZE_BYTES:-1048576}"

# pre-commit 允许跳过大文件限制的路径和扩展名，空格分隔。
GIT_HOOK_LARGE_FILE_ALLOW_PATTERNS="${GIT_HOOK_LARGE_FILE_ALLOW_PATTERNS:-docs/design/figma/* docs/assets/* *.png *.jpg *.jpeg *.gif *.webp *.pdf}"

# pre-push：路径匹配后执行的命令。格式：pattern::command，多项用换行分隔。
# 示例：
# GIT_HOOK_PATH_COMMANDS=$'web/*::npm --prefix web run build\nservices/*/src/main/*::make test-module MODULE=%MODULE%'
GIT_HOOK_PATH_COMMANDS="${GIT_HOOK_PATH_COMMANDS:-}"

# pre-push：路径匹配后要求同模块测试路径也有改动。格式：
# code_pattern::test_pattern::message
# 示例：
# GIT_HOOK_REQUIRED_TEST_RULES=$'services/*/src/main/*::%MODULE%/tests/*::当前模块代码改动后需要补充对应测试。'
GIT_HOOK_REQUIRED_TEST_RULES="${GIT_HOOK_REQUIRED_TEST_RULES:-}"

# 项目覆盖配置。不同项目通常只需要调整该文件或通过环境变量覆盖。
if [[ -n "${GIT_HOOK_PROJECT_CONFIG:-}" && -f "$GIT_HOOK_PROJECT_CONFIG" ]]; then
  # shellcheck disable=SC1090
  source "$GIT_HOOK_PROJECT_CONFIG"
elif [[ -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/git-hook.project.config.sh" ]]; then
  # shellcheck disable=SC1091
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/git-hook.project.config.sh"
fi
