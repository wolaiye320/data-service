#!/usr/bin/env bash
# 一键串联步骤 1 -> 5（交互式引导 push）
# 用法:
#   ./bin/git-step-all.sh <path...>
#   ./bin/git-step-all.sh --skip-review <path...>

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

skip_review=false
if [[ "${1:-}" == "--skip-review" ]]; then
  skip_review=true
  shift
fi

if [[ "$#" -lt 1 ]]; then
  cat <<'EOF'
用法:
  ./bin/git-step-all.sh <path...>
  ./bin/git-step-all.sh --skip-review <path...>

示例:
  ./bin/git-step-all.sh AGENTS.md bin .cursor/rules/git/git.mdc
EOF
  exit 1
fi

echo "=== Git Step All (1->5) ==="

# 如果指定路径下已有暂存文件，先询问是否跳过第1步，避免重复输出。
if [[ "$skip_review" == "false" ]]; then
  has_staged_in_paths=false
  while IFS= read -r -d '' staged_file; do
    for path in "$@"; do
      clean_path="${path#./}"
      clean_path="${clean_path%/}"
      if [[ -z "$clean_path" ]]; then
        clean_path="."
      fi
      if [[ "$clean_path" == "." ]] || [[ "$staged_file" == "$clean_path" ]] || [[ "$staged_file" == "$clean_path"/* ]]; then
        has_staged_in_paths=true
        break 2
      fi
    done
  done < <(git diff --cached --name-only --diff-filter=ACMR -z)

  if [[ "$has_staged_in_paths" == "true" ]]; then
    echo
    read -r -p "检测到目标路径已有暂存文件，是否跳过第1步审阅？(y/N): " skip_review_confirm
    if [[ "$skip_review_confirm" =~ ^[Yy]$ ]]; then
      skip_review=true
    fi
  fi
fi

if [[ "$skip_review" == "false" ]]; then
  echo
  echo "[1/5] 审阅改动"
  ./bin/git-step-1-review.sh "$@"
else
  echo
  echo "[1/5] 审阅改动（已跳过）"
fi

echo
echo "[2/5] 暂存变更"
./bin/git-step-2-stage.sh "$@"

echo
echo "[3/5] 执行检查并生成草稿"
./bin/git-step-3-check.sh "$@"

read -r -p "是否使用草稿提交？(y/N): " confirm
if [[ "$confirm" =~ ^[Yy]$ ]]; then
  echo
  echo "[4/5] 使用草稿提交"
  ./bin/git-step-4-commit.sh --from-draft

  current_branch="$(git rev-parse --abbrev-ref HEAD)"
  echo
  read -r -p "是否立即推送到远程？(y/N): " push_now
  if [[ "$push_now" =~ ^[Yy]$ ]]; then
    read -r -p "remote 名称（默认 origin）: " input_remote
    read -r -p "branch 名称（默认 ${current_branch}）: " input_branch
    read -r -p "是否跳过测试？(y/N): " skip_integration
    remote="${input_remote:-origin}"
    branch="${input_branch:-$current_branch}"

    echo
    if [[ "$skip_integration" =~ ^[Yy]$ ]]; then
      echo "[5/5] 推送到远程 (${remote} ${branch}) - 跳过测试"
      ./bin/git-step-5-push.sh --skip-tests "$remote" "$branch"
    else
      echo "[5/5] 推送到远程 (${remote} ${branch}) - 执行 mvn test"
      ./bin/git-step-5-push.sh "$remote" "$branch"
    fi
  else
    echo
    echo "[5/5] 推送步骤跳过"
    echo "如需后续推送，可执行:"
    echo "  ./bin/git-step-5-push.sh [remote] [branch]            # 执行 mvn test"
    echo "  ./bin/git-step-5-push.sh --skip-tests [remote] [branch]  # 跳过测试"
  fi

  echo
  echo "流程完成。"
else
  echo
  echo "已取消自动提交。你可手动执行:"
  echo "  ./bin/git-step-4-commit.sh --from-draft"
  echo "或"
  echo "  ./bin/git-step-4-commit.sh -m \"type(scope): subject\""
fi
