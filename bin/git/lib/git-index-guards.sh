#!/usr/bin/env bash

run_common_pre_commit_checks() {
  local project_root="${1:-}"
  shift || true

  local staged_files=("$@")
  local file=""
  local check_errors=""
  local conflict_errors=""
  local config_script=""
  local max_bytes=0
  local size=""
  local doc_check_script=""
  local allow_patterns=()
  local allowed=false

  if [[ -z "$project_root" || "${#staged_files[@]}" -eq 0 ]]; then
    return 0
  fi

  config_script="$project_root/bin/git/git-hook.config.sh"
  if [[ -f "$config_script" ]]; then
    # shellcheck disable=SC1090
    source "$config_script"
  fi

  max_bytes="${GIT_HOOK_MAX_FILE_SIZE_BYTES:-1048576}"
  read -r -a allow_patterns <<< "${GIT_HOOK_LARGE_FILE_ALLOW_PATTERNS:-docs/design/figma/* docs/assets/* *.png *.jpg *.jpeg *.gif *.webp *.pdf}"

  echo "[pre-commit] validating staged changes..."

  for file in "${staged_files[@]}"; do
    if [[ "$file" =~ (^|/)\.env(\..*)?$ ]] || \
       [[ "$file" =~ \.pem$ ]] || \
       [[ "$file" =~ \.key$ ]] || \
       [[ "$file" =~ (^|/)id_rsa(\.pub)?$ ]] || \
       [[ "$file" =~ (^|/)credentials(\.json|\.ya?ml|\.txt)?$ ]]; then
      echo "[pre-commit] blocked potential secret file: $file"
      echo "Remove it from commit, then retry."
      return 1
    fi
  done

  check_errors="$(git diff --cached --check -- "${staged_files[@]}" 2>&1 || true)"
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    if [[ "$line" == *"conflict marker"* ]]; then
      conflict_errors+="$line"$'\n'
    fi
  done <<< "$check_errors"

  if [[ -n "${conflict_errors//$'\n'/}" ]]; then
    echo "[pre-commit] staged patch has unresolved conflict markers."
    printf '%s' "$conflict_errors"
    return 1
  fi

  if [[ -n "${GIT_HOOK_DOC_FILENAME_CHECK_SCRIPT:-}" && -x "$project_root/$GIT_HOOK_DOC_FILENAME_CHECK_SCRIPT" ]]; then
    doc_check_script="$project_root/$GIT_HOOK_DOC_FILENAME_CHECK_SCRIPT"
  elif [[ -x "$project_root/bin/git/git-check-doc-filenames.sh" ]]; then
    doc_check_script="$project_root/bin/git/git-check-doc-filenames.sh"
  fi

  if [[ -n "$doc_check_script" ]]; then
    "$doc_check_script" --staged
  fi

  for file in "${staged_files[@]}"; do
    allowed=false
    for pattern in "${allow_patterns[@]}"; do
      if [[ "$file" == $pattern ]]; then
        allowed=true
        break
      fi
    done
    if [[ "$allowed" == "true" ]]; then
      continue
    fi

    size="$(git cat-file -s ":$file" 2>/dev/null || echo 0)"
    if [[ "$size" =~ ^[0-9]+$ ]] && (( size > max_bytes )); then
      echo "[pre-commit] blocked large file: $file (${size} bytes > ${max_bytes})."
      echo "If required, split or handle it outside regular source commit."
      return 1
    fi
  done

  echo "[pre-commit] passed."
}
