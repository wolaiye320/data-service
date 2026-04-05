#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(git rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"

reset_first=true
paths=()

usage() {
  cat <<'EOF'
用法:
  ./bin/git/git-stage.sh <path...>
  ./bin/git/git-stage.sh --append <path...>

选项:
  --append     保留现有暂存区，在其基础上追加指定路径
  --replace    先清空现有暂存区，再暂存指定路径（默认）
  -h, --help   显示帮助

示例:
  ./bin/git/git-stage.sh bin/README.md bin/git/git-stage.sh
  ./bin/git/git-stage.sh --append docs/research/common/Git与协作规范.md
EOF
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --append)
      reset_first=false
      shift
      ;;
    --replace)
      reset_first=true
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
      paths+=("$1")
      shift
      ;;
  esac
done

if [[ "${#paths[@]}" -eq 0 ]]; then
  usage
  exit 1
fi

if [[ "$reset_first" == "true" ]]; then
  git reset HEAD --quiet
fi

git add "${paths[@]}"

echo "[git-stage] staged files:"
git diff --cached --name-only --diff-filter=ACMR
