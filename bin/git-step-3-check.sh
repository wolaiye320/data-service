#!/usr/bin/env bash
# 步骤 3：审阅暂存区、执行 pre-commit gate，并生成提交草稿
# 用法:
#   ./bin/git-step-3-check.sh [path...]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

./bin/git-check.sh "$@"

echo
echo "[step-3] 生成提交草稿"
./bin/git-step-3b-draft-msg.sh

echo
echo "----- COMMIT DRAFT -----"
cat .git/COMMIT_DRAFT_MSG
echo "------------------------"
