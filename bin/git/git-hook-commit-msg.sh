#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/git-hook.config.sh"

msg_file="${1:-}"
if [[ -z "$msg_file" || ! -f "$msg_file" ]]; then
  echo "[commit-msg] missing commit message file"
  exit 1
fi

IFS= read -r first_line < "$msg_file" || true

if [[ "$GIT_HOOK_ALLOW_MERGE_COMMITS" == "true" && "$first_line" =~ ^Merge\  ]]; then
  exit 0
fi

if [[ "$GIT_HOOK_ALLOW_REVERT_COMMITS" == "true" && "$first_line" =~ ^Revert\ \" ]]; then
  exit 0
fi

types_pattern="$(printf '%s' "$GIT_HOOK_COMMIT_TYPES" | tr ' ' '|' )"
pattern="^(${types_pattern})\\(${GIT_HOOK_SCOPE_REGEX}\\): .+\$"

if [[ ! "$first_line" =~ $pattern ]]; then
  cat <<EOF
[commit-msg] invalid commit message format.
Required: <type>(<scope>): <subject>

Valid types:
  $GIT_HOOK_COMMIT_TYPES

Example:
  feat(web): add quality tickets page
EOF
  exit 1
fi
