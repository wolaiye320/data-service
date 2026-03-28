#!/usr/bin/env bash
# 步骤 2：将本次提交范围加入暂存区

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

if [[ "$#" -lt 1 ]]; then
  cat <<'EOF'
用法:
  ./bin/git-step-2-stage.sh <path...>

示例:
  ./bin/git-step-2-stage.sh AGENTS.md bin/git-check.sh
  ./bin/git-step-2-stage.sh .cursor/rules/git/git.mdc bin/README.md
EOF
  exit 1
fi

# 先重置暂存区，确保只提交指定的路径
git reset HEAD > /dev/null 2>&1 || true

git add "$@"
echo "[step-2] staged paths:"
git diff --cached --name-only --diff-filter=ACMR
