#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(git -C "$SCRIPT_DIR/../.." rev-parse --show-toplevel)"
source "$PROJECT_ROOT/bin/git/lib/git-index-guards.sh"

staged_files=()
while IFS= read -r -d '' file; do
  staged_files+=("$file")
done < <(git diff --cached --name-only --diff-filter=ACMR -z)

if [[ "${#staged_files[@]}" -eq 0 ]]; then
  exit 0
fi

run_common_pre_commit_checks "$PROJECT_ROOT" "${staged_files[@]}"
