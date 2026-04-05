#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(git rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"

source "$SCRIPT_DIR/lib/git-common.sh"

remote=""
branch=""
skip_hook=false
no_verify=false
before_push_cmds=()
positionals=()

usage() {
  cat <<'EOF'
用法:
  ./bin/git/git-push-safe.sh
  ./bin/git/git-push-safe.sh --remote origin --branch feature/example
  ./bin/git/git-push-safe.sh --before-push-cmd "make test"
  ./bin/git/git-push-safe.sh --skip-hook --no-verify origin feature/example

选项:
  --remote <name>             指定 remote，默认 origin
  --branch <name>             指定 branch，默认当前分支
  --before-push-cmd <cmd>     在 git push 前执行命令，可重复传入
  --skip-hook                 跳过 .githooks/pre-push
  --no-verify                 git push 时附带 --no-verify
  -h, --help                  显示帮助
EOF
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
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
    --skip-hook)
      skip_hook=true
      shift
      ;;
    --no-verify)
      no_verify=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      echo "未知参数: $1"
      usage
      exit 1
      ;;
    *)
      positionals+=("$1")
      shift
      ;;
  esac
done

if [[ -z "$remote" && "${#positionals[@]}" -ge 1 ]]; then
  remote="${positionals[0]}"
fi
if [[ -z "$branch" && "${#positionals[@]}" -ge 2 ]]; then
  branch="${positionals[1]}"
fi

if [[ -z "$remote" ]]; then
  remote="origin"
fi
if [[ -z "$branch" ]]; then
  branch="$(git rev-parse --abbrev-ref HEAD)"
fi

if [[ "$skip_hook" == "false" ]]; then
  if run_optional_project_hook pre-push; then
    :
  else
    echo "[git-push] .githooks/pre-push not found, skip hook."
  fi
fi

for cmd in "${before_push_cmds[@]-}"; do
  [[ -n "$cmd" ]] || continue
  echo "[git-push] running: $cmd"
  bash -lc "$cmd"
done

echo "[git-push] pushing to ${remote} ${branch}"
if [[ "$no_verify" == "true" ]]; then
  git push --no-verify "$remote" "$branch"
else
  git push "$remote" "$branch"
fi
