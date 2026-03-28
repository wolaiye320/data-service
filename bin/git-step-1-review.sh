#!/usr/bin/env bash
# 步骤 1：审阅工作区改动（只读）
# 用法:
#   ./bin/git-step-1-review.sh [path...]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

./bin/git-check.sh --review-only "$@"
