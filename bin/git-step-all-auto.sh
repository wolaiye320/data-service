#!/usr/bin/env bash
# 非交互式git提交脚本,用于AI自动提交
# 用法:
#   ./bin/git-step-all-auto.sh <path...>
#   ./bin/git-step-all-auto.sh --auto-push <path...>
#   ./bin/git-step-all-auto.sh --auto-push --skip-tests <path...>
#   ./bin/git-step-all-auto.sh --remote origin --branch main <path...>
#
# 参数:
#   --auto-push                自动推送到远程(默认不推送)
#   --skip-tests               推送时跳过 mvn test
#   --remote <name>            指定远程仓库名称(默认origin)
#   --branch <name>            指定分支名称(默认当前分支)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# 默认参数
auto_push=false
skip_tests=false
remote="origin"
branch=""

# 解析参数
paths=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --auto-push)
      auto_push=true
      shift
      ;;
    --skip-tests|--skip-integration-test)
      skip_tests=true
      shift
      ;;
    --remote)
      remote="$2"
      shift 2
      ;;
    --branch)
      branch="$2"
      shift 2
      ;;
    *)
      paths+=("$1")
      shift
      ;;
  esac
done

# 检查是否提供了路径
if [[ ${#paths[@]} -eq 0 ]]; then
  cat <<'EOF'
用法:
  ./bin/git-step-all-auto.sh <path...>
  ./bin/git-step-all-auto.sh --auto-push <path...>
  ./bin/git-step-all-auto.sh --auto-push --skip-tests <path...>
  ./bin/git-step-all-auto.sh --remote origin --branch main <path...>

参数:
  --auto-push                自动推送到远程(默认不推送)
  --skip-tests               推送时跳过 mvn test
  --remote <name>            指定远程仓库名称(默认origin)
  --branch <name>            指定分支名称(默认当前分支)

示例:
  ./bin/git-step-all-auto.sh data-service-app/pom.xml
  ./bin/git-step-all-auto.sh --auto-push bin/git-step-all-auto.sh CLAUDE.md
  ./bin/git-step-all-auto.sh --auto-push --skip-tests .
EOF
  exit 1
fi

echo "=== Git Step All Auto (非交互式) ==="
echo

# 步骤1: 跳过审阅(AI已经知道要提交什么)
echo "[1/5] 审阅改动（自动跳过）"
echo

# 步骤2: 暂存变更
echo "[2/5] 暂存变更"
./bin/git-step-2-stage.sh "${paths[@]}"
echo

# 步骤3: 执行检查并生成草稿
echo "[3/5] 执行检查并生成草稿"
./bin/git-step-3-check.sh "${paths[@]}"
echo

# 步骤4: 自动使用草稿提交
echo "[4/5] 使用草稿提交（自动）"
./bin/git-step-4-commit.sh --from-draft
echo

# 步骤5: 推送到远程(如果指定了--auto-push)
if [[ "$auto_push" == "true" ]]; then
  # 如果没有指定分支,使用当前分支
  if [[ -z "$branch" ]]; then
    branch="$(git rev-parse --abbrev-ref HEAD)"
  fi

  if [[ "$skip_tests" == "true" ]]; then
    echo "[5/5] 推送到远程 (${remote} ${branch}) - 跳过测试（自动）"
    ./bin/git-step-5-push.sh --skip-tests "$remote" "$branch"
  else
    echo "[5/5] 推送到远程 (${remote} ${branch}) - 执行 mvn test（自动）"
    ./bin/git-step-5-push.sh "$remote" "$branch"
  fi
else
  echo "[5/5] 推送步骤跳过（未指定--auto-push）"
  echo "如需推送，可执行:"
  echo "  ./bin/git-step-5-push.sh [remote] [branch]            # 执行 mvn test"
  echo "  ./bin/git-step-5-push.sh --skip-tests [remote] [branch]  # 跳过测试"
fi

echo
echo "流程完成。"
