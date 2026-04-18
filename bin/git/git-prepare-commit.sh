#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(git rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"

draft_args=()
check_args=()

usage() {
  cat <<'EOF'
用法:
  ./bin/git/git-prepare-commit.sh [path...]
  ./bin/git/git-prepare-commit.sh --scope repo --type chore --subject "update helper scripts"

说明:
  1. 执行 git-check
  2. 生成提交草稿

支持透传给草稿生成的参数:
  --scope <value>
  --type <value>
  --subject <value>
  --draft-file <path>
EOF
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --scope|--type|--subject|--draft-file)
      draft_args+=("$1" "${2:-}")
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      check_args+=("$1")
      shift
      ;;
  esac
done

if [[ "${#check_args[@]}" -eq 0 ]]; then
  ./bin/git/git-check.sh
else
  ./bin/git/git-check.sh "${check_args[@]}"
fi

echo
echo "[git-prepare] generating commit draft"
./bin/git/git-draft-commit-message.sh "${draft_args[@]+"${draft_args[@]}"}"
