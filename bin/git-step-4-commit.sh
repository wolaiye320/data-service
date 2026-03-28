#!/usr/bin/env bash
# 步骤 4：提交（自动触发 commit-msg 校验）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

draft_file=".git/COMMIT_DRAFT_MSG"

if [[ "${1:-}" == "--from-draft" ]]; then
  if [[ ! -f "$draft_file" ]]; then
    echo "[step-4] draft not found: $draft_file"
    echo "run: ./bin/git-step-3b-draft-msg.sh"
    exit 1
  fi
  if git diff --cached --quiet; then
    echo "[step-4] no staged changes. run step-2 first."
    exit 1
  fi
  git commit -F "$draft_file"
  exit 0
fi

if [[ "${1:-}" != "-m" || -z "${2:-}" ]]; then
  cat <<'EOF'
用法:
  ./bin/git-step-4-commit.sh -m "type(scope): subject"
  ./bin/git-step-4-commit.sh --from-draft

示例:
  ./bin/git-step-4-commit.sh -m "chore(git): add guided git step scripts"
EOF
  exit 1
fi

if git diff --cached --quiet; then
  echo "[step-4] no staged changes. run step-2 first."
  exit 1
fi

git commit -m "$2"
