#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(git -C "$SCRIPT_DIR/../.." rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"

source "$SCRIPT_DIR/git-hook.config.sh"
source "$SCRIPT_DIR/lib/git-common.sh"

echo "[pre-push] running lightweight gate..."

substitute_module() {
  local text="${1:-}"
  local module="${2:-}"
  printf '%s' "${text//%MODULE%/$module}"
}

base_ref="$(resolve_git_base_ref)"
changed_files="$(git diff --name-only "${base_ref}...HEAD")"
if [[ -z "$changed_files" ]]; then
  echo "[pre-push] no changes vs ${base_ref}, skip checks."
  exit 0
fi

commands_to_run=()
required_test_failures=()

while IFS= read -r file; do
  [[ -n "$file" ]] || continue

  while IFS= read -r rule; do
    [[ -n "$rule" ]] || continue
    pattern="${rule%%::*}"
    command="${rule#*::}"
    if [[ "$file" == $pattern ]]; then
      module="${file%%/*}"
      commands_to_run+=("$(substitute_module "$command" "$module")")
    fi
  done <<< "$GIT_HOOK_PATH_COMMANDS"

  while IFS= read -r rule; do
    [[ -n "$rule" ]] || continue
    code_pattern="${rule%%::*}"
    rest="${rule#*::}"
    test_pattern="${rest%%::*}"
    message="${rest#*::}"
    if [[ "$file" == $code_pattern ]]; then
      module="${file%%/*}"
      resolved_test_pattern="$(substitute_module "$test_pattern" "$module")"
      if ! printf '%s\n' "$changed_files" | awk -v p="$resolved_test_pattern" '
        function glob_to_regex(glob,    regex) {
          regex = glob
          gsub(/\./, "\\.", regex)
          gsub(/\*/, ".*", regex)
          return "^" regex "$"
        }
        BEGIN { regex = glob_to_regex(p) }
        $0 ~ regex { found=1 }
        END { exit !found }
      '; then
        required_test_failures+=("${module}::$(substitute_module "$message" "$module")")
      fi
    fi
  done <<< "$GIT_HOOK_REQUIRED_TEST_RULES"
done <<< "$changed_files"

if (( ${#required_test_failures[@]} > 0 )); then
  echo "[pre-push] blocked:"
  printed=()
  for failure in "${required_test_failures[@]}"; do
    module="${failure%%::*}"
    message="${failure#*::}"
    already_printed=false
    for printed_item in "${printed[@]}"; do
      if [[ "$printed_item" == "$module::$message" ]]; then
        already_printed=true
        break
      fi
    done
    if [[ "$already_printed" == "false" ]]; then
      echo "  - ${module}: ${message}"
      printed+=("$module::$message")
    fi
  done
  exit 1
fi

deduped_commands="$(printf '%s\n' "${commands_to_run[@]+"${commands_to_run[@]}"}" | awk 'NF && !seen[$0]++')"
while IFS= read -r command; do
  [[ -n "$command" ]] || continue
  echo "[pre-push] running: $command"
  bash -lc "$command"
done <<< "$deduped_commands"

echo "[pre-push] passed."
