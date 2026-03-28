#!/usr/bin/env bash
# 步骤 5：推送前执行项目测试，再执行 git push
# 用法:
#   ./bin/git-step-5-push.sh [--skip-tests|--skip-integration-test] [remote] [branch]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

SKIP_TESTS=false
if [[ "${1:-}" == "--skip-tests" || "${1:-}" == "--skip-integration-test" ]]; then
  SKIP_TESTS=true
  shift
fi

remote="${1:-origin}"
branch="${2:-$(git rev-parse --abbrev-ref HEAD)}"

if [[ "$remote" == .* ]] || [[ "$remote" == */* ]]; then
  cat <<'EOF'
[step-5] 参数错误：第一个参数应为 remote 名称，而不是路径。

正确用法：
  ./bin/git-step-5-push.sh
  ./bin/git-step-5-push.sh --skip-tests
  ./bin/git-step-5-push.sh origin
  ./bin/git-step-5-push.sh --skip-tests origin main
EOF
  exit 1
fi

if [[ "$SKIP_TESTS" == "true" ]]; then
  echo "[step-5] 跳过 mvn test，执行 pre-push gate"
  ./bin/git-check.sh --push-gate
  git push "$remote" "$branch"
else
  echo "[step-5] 运行项目测试"
  mvn -q test
  echo "[step-5] 测试通过，开始推送"
  git push --no-verify "$remote" "$branch"
fi
