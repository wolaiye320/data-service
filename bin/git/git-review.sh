#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/git-common.sh"

echo "=== Git Review Snapshot ==="
echo "repo   : $(basename "$(pwd)")"
echo "branch : $(git rev-parse --abbrev-ref HEAD)"
echo "head   : $(git rev-parse --short HEAD)"
echo

base_ref="$(resolve_git_base_ref)"

echo "base ref: ${base_ref}"
echo

echo "--- Working tree status ---"
git status --short
echo

echo "--- Staged files ---"
staged="$(git diff --cached --name-only --diff-filter=ACMR)"
if [[ -z "${staged}" ]]; then
  echo "(none)"
else
  printf "%s\n" "${staged}"
fi
echo

echo "--- Staged diffstat ---"
if git diff --cached --quiet; then
  echo "(none)"
else
  git diff --cached --stat
fi
echo

echo "--- Commits vs base ---"
git log --oneline "${base_ref}..HEAD" || true
echo

echo "--- Changed files vs base ---"
git diff --name-only "${base_ref}...HEAD" || true
echo

echo "--- Risk scan (keywords) ---"
git diff --cached --name-only | awk '
  /\.sql$/ || /V[0-9]+__.*\.sql$/ {print "[db] " $0}
  /\.ya?ml$/ || /\.properties$/ || /\.env/ {print "[config] " $0}
  /(security|auth|permission|role)/ {print "[security] " $0}
' || true
echo

echo "Tip: use this output as PR self-review checklist."
