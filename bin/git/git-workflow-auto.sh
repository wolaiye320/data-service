#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(git rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"

auto_push=false
skip_review=true
append_stage=false
remote=""
branch=""
draft_args=()
before_push_cmds=()
paths=()

usage() {
  cat <<'EOF'
用法:
  ./bin/git/git-workflow-auto.sh <path...>
  ./bin/git/git-workflow-auto.sh --auto-push <path...>
  ./bin/git/git-workflow-auto.sh --auto-push --before-push-cmd "make test" <path...>

选项:
  --auto-push                 提交后自动推送
  --skip-review               跳过 git-review（默认开启）
  --with-review               显式执行 git-review
  --append-stage              暂存时保留现有暂存区
  --remote <name>             指定 remote
  --branch <name>             指定 branch
  --before-push-cmd <cmd>     推送前附加执行命令，可重复传入
  --scope <value>             覆盖 commit scope
  --type <value>              覆盖 commit type
  --subject <value>           覆盖 commit subject
EOF
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --auto-push)
      auto_push=true
      shift
      ;;
    --skip-review)
      skip_review=true
      shift
      ;;
    --with-review)
      skip_review=false
      shift
      ;;
    --append-stage)
      append_stage=true
      shift
      ;;
    --remote)
      remote="${2:-}"
      shift 2
      ;;
    --branch)
      branch="${2:-}"
      shift 2
      ;;
    --before-push-cmd)
      before_push_cmds+=("${2:-}")
      shift 2
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

echo "=== Git Workflow Auto ==="

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
if [[ "$append_stage" == "true" ]]; then
  ./bin/git/git-stage.sh --append "${paths[@]}"
else
  ./bin/git/git-stage.sh "${paths[@]}"
fi

echo
echo "[3/5] 校验并生成草稿"
./bin/git/git-prepare-commit.sh "${draft_args[@]+"${draft_args[@]}"}"

echo
echo "[4/5] 使用草稿提交"
./bin/git/git-commit-staged.sh --from-draft

if [[ "$auto_push" == "false" ]]; then
  echo
  echo "[5/5] 推送步骤跳过（未指定 --auto-push）"
  exit 0
fi

push_args=()
if [[ -n "$remote" ]]; then
  push_args+=(--remote "$remote")
fi
if [[ -n "$branch" ]]; then
  push_args+=(--branch "$branch")
fi
for cmd in "${before_push_cmds[@]+"${before_push_cmds[@]}"}"; do
  push_args+=(--before-push-cmd "$cmd")
done

echo
echo "[5/5] 推送到远程"
./bin/git/git-push-safe.sh "${push_args[@]+"${push_args[@]}"}"
