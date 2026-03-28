#!/usr/bin/env bash
# 步骤 3b：根据暂存区生成提交信息草稿

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

draft_file=".git/COMMIT_DRAFT_MSG"

staged_files="$(git diff --cached --name-only --diff-filter=ACMR)"
if [[ -z "$staged_files" ]]; then
  echo "[step-3b] no staged changes. run step-2 first."
  exit 1
fi

detect_scope() {
  if echo "$staged_files" | grep -qE '^data-service-web/'; then
    echo "web"
  elif echo "$staged_files" | grep -qE '^data-service-app/'; then
    echo "app"
  elif echo "$staged_files" | grep -qE '^data-service-application/'; then
    echo "application"
  elif echo "$staged_files" | grep -qE '^data-service-common/'; then
    echo "common"
  elif echo "$staged_files" | grep -qE '^data-service-domain/'; then
    echo "domain"
  elif echo "$staged_files" | grep -qE '^data-service-federation/'; then
    echo "federation"
  elif echo "$staged_files" | grep -qE '^data-service-infrastructure/'; then
    echo "infrastructure"
  elif echo "$staged_files" | grep -qE '^data-service-interfaces/'; then
    echo "interfaces"
  elif echo "$staged_files" | grep -qE '^docs/' || echo "$staged_files" | grep -qE '\.md$'; then
    echo "docs"
  else
    echo "repo"
  fi
}

detect_type() {
  local total docs cfg tests code
  total="$(echo "$staged_files" | awk 'NF' | wc -l | tr -d ' ')"
  docs="$(echo "$staged_files" | grep -cE '^docs/|\.md$' || true)"
  cfg="$(echo "$staged_files" | grep -cE '^\.cursor/|^\.githooks/|^bin/|^scripts/|pom\.xml|package\.json|pnpm-lock\.yaml' || true)"
  tests="$(echo "$staged_files" | grep -cE 'src/test/|test|spec' || true)"
  code="$(echo "$staged_files" | grep -cE '\.(java|ts|tsx|js|jsx|py|go|sql)$' || true)"

  # 确保变量是数字
  docs="${docs:-0}"
  cfg="${cfg:-0}"
  tests="${tests:-0}"
  code="${code:-0}"

  if (( docs > 0 && docs == total )); then
    echo "docs"
  elif (( tests > 0 && tests == total )); then
    echo "test"
  elif (( cfg > 0 && code == 0 )); then
    echo "chore"
  elif (( code > 0 )); then
    echo "feat"
  else
    echo "chore"
  fi
}

scope="$(detect_scope)"
type="$(detect_type)"

subject="update staged changes"
if [[ "$type" == "docs" ]]; then
  subject="update project documentation"
elif [[ "$type" == "test" ]]; then
  subject="add or update tests"
elif [[ "$type" == "chore" ]]; then
  subject="update tooling and workflow scripts"
fi

{
  echo "${type}(${scope}): ${subject}"
  echo
  echo "# bullet suggestions (edit before commit if needed)"
  git diff --cached --name-only --diff-filter=ACMR | sed 's#^#- update #' | head -n 8
} > "$draft_file"

echo "[step-3b] draft created: ${draft_file}"
echo
cat <<'EOF'
下一步：
1) 检查草稿:  cat .git/COMMIT_DRAFT_MSG
2) 使用草稿提交: ./bin/git-step-4-commit.sh --from-draft
EOF
