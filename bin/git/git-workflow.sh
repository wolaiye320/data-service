#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(git rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"

skip_review=false
draft_args=()
paths=()

usage() {
  cat <<'EOF'
用法:
  ./bin/git/git-workflow.sh <path...>
  ./bin/git/git-workflow.sh --skip-review <path...>
  ./bin/git/git-workflow.sh --scope repo --type chore --subject "update helper scripts" <path...>
EOF
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --skip-review)
      skip_review=true
      shift
      ;;
    --scope|--type|--subject|--draft-file)
      draft_args+=("$1" "${2:-}")
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      paths+=("$1")
      shift
      ;;
  esac
done

if [[ "${#paths[@]}" -eq 0 ]]; then
  usage
  exit 1
fi

echo "=== Git Workflow ==="

if [[ "$skip_review" == "false" ]]; then
  echo
  echo "[1/5] 审阅改动"
  ./bin/git/git-review.sh
else
  echo
  echo "[1/5] 审阅改动（已跳过）"
fi

echo
echo "[2/5] 暂存变更"
./bin/git/git-stage.sh "${paths[@]}"

echo
echo "[3/5] 校验并生成草稿"
./bin/git/git-prepare-commit.sh "${draft_args[@]}"

read -r -p "是否使用草稿提交？(y/N): " confirm_commit
if [[ ! "$confirm_commit" =~ ^[Yy]$ ]]; then
  echo "已取消自动提交。"
  echo "你可以稍后执行: ./bin/git/git-commit-staged.sh --from-draft"
  exit 0
fi

echo
echo "[4/5] 使用草稿提交"
./bin/git/git-commit-staged.sh --from-draft

read -r -p "是否立即推送？(y/N): " confirm_push
if [[ ! "$confirm_push" =~ ^[Yy]$ ]]; then
  echo
  echo "[5/5] 推送步骤跳过"
  echo "如需后续推送，可执行: ./bin/git/git-push-safe.sh"
  exit 0
fi

current_branch="$(git rev-parse --abbrev-ref HEAD)"
read -r -p "remote 名称（默认 origin）: " input_remote
read -r -p "branch 名称（默认 ${current_branch}）: " input_branch
read -r -p "是否额外执行自定义命令再推送？(y/N): " confirm_extra

push_args=()
push_args+=(--remote "${input_remote:-origin}")
push_args+=(--branch "${input_branch:-$current_branch}")

if [[ "$confirm_extra" =~ ^[Yy]$ ]]; then
  read -r -p "请输入推送前命令: " before_push_cmd
  if [[ -n "${before_push_cmd:-}" ]]; then
    push_args+=(--before-push-cmd "$before_push_cmd")
  fi
fi

echo
echo "[5/5] 推送到远程"
./bin/git/git-push-safe.sh "${push_args[@]}"
