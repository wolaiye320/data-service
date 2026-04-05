#!/usr/bin/env bash

load_git_hook_config() {
  local project_root=""
  project_root="$(git_repo_root)"
  if [[ -f "$project_root/bin/git/git-hook.config.sh" ]]; then
    # shellcheck disable=SC1090
    source "$project_root/bin/git/git-hook.config.sh"
  fi
}

resolve_git_base_ref() {
  load_git_hook_config

  if [[ -n "${GIT_HOOK_BASE_REF:-}" ]]; then
    printf '%s\n' "$GIT_HOOK_BASE_REF"
    return
  fi

  local candidate=""
  for candidate in ${GIT_HOOK_BASE_REF_CANDIDATES:-origin/main origin/master upstream/main upstream/master}; do
    if git rev-parse --verify "$candidate" >/dev/null 2>&1; then
      printf '%s\n' "$candidate"
      return
    fi
  done

  git rev-list --max-parents=0 HEAD | tail -n 1
}

git_repo_root() {
  git rev-parse --show-toplevel 2>/dev/null
}

git_draft_file_path() {
  local project_root
  project_root="$(git_repo_root)"
  printf '%s/.git/COMMIT_DRAFT_MSG' "$project_root"
}

normalize_git_path() {
  local path="${1:-}"
  if [[ "$path" == "." || "$path" == "./" || -z "$path" ]]; then
    echo "__ALL__"
    return
  fi

  path="${path#./}"
  path="${path%/}"

  if [[ -z "$path" ]]; then
    echo "__ALL__"
    return
  fi

  echo "$path"
}

print_matching_staged_files() {
  if [[ "$#" -eq 0 ]]; then
    git diff --cached --name-only --diff-filter=ACMR -z 2>/dev/null | tr '\0' '\n'
    return
  fi

  local file=""
  local path=""
  local clean_path=""
  while IFS= read -r -d '' file; do
    for path in "$@"; do
      clean_path="$(normalize_git_path "$path")"
      if [[ "$clean_path" == "__ALL__" ]] || [[ "$file" == "$clean_path" ]] || [[ "$file" == "$clean_path"/* ]]; then
        printf '%s\n' "$file"
        break
      fi
    done
  done < <(git diff --cached --name-only --diff-filter=ACMR -z 2>/dev/null)
}

print_staged_files_array() {
  while IFS= read -r -d '' file; do
    printf '%s\n' "$file"
  done < <(git diff --cached --name-only --diff-filter=ACMR -z 2>/dev/null)
}

sanitize_commit_scope() {
  local scope="${1:-repo}"
  scope="$(printf '%s' "$scope" | tr '[:upper:]' '[:lower:]')"
  scope="$(printf '%s' "$scope" | sed 's#[^a-z0-9._/-]#-#g')"
  scope="$(printf '%s' "$scope" | sed 's#--*#-#g; s#^[-/]*##; s#[-/]*$##')"
  if [[ -z "$scope" ]]; then
    scope="repo"
  fi
  printf '%s' "$scope"
}

infer_commit_scope_from_files() {
  local files="${1:-}"
  local top_levels=""
  local count=0
  local scope=""

  if [[ -z "$files" ]]; then
    echo "repo"
    return
  fi

  if printf '%s\n' "$files" | awk 'NF && ($0 ~ /^docs\// || $0 ~ /\.md$/) { next } NF { exit 1 }'; then
    echo "docs"
    return
  fi

  top_levels="$(printf '%s\n' "$files" | awk -F/ 'NF { print ($1 == $0 ? "." : $1) }' | sort -u)"
  count="$(printf '%s\n' "$top_levels" | awk 'NF { count++ } END { print count + 0 }')"

  if [[ "$count" -eq 1 ]]; then
    scope="$(printf '%s\n' "$top_levels" | awk 'NF { print; exit }')"
    if [[ "$scope" == "." ]]; then
      echo "repo"
    else
      sanitize_commit_scope "$scope"
    fi
    return
  fi

  echo "repo"
}

infer_commit_type_from_files() {
  local files="${1:-}"
  local total=0
  local docs=0
  local tests=0
  local config=0
  local code=0

  total="$(printf '%s\n' "$files" | awk 'NF { count++ } END { print count + 0 }')"
  docs="$(printf '%s\n' "$files" | awk '/^docs\/|\.md$/ { count++ } END { print count + 0 }')"
  tests="$(printf '%s\n' "$files" | awk '/(^|\/)(test|tests|spec|__tests__)(\/|$)|(\.|-)(spec|test)\./ { count++ } END { print count + 0 }')"
  config="$(printf '%s\n' "$files" | awk '/(^|\/)(bin|scripts|\.githooks|\.github)(\/|$)|(^|\/)(Makefile|Dockerfile|pom\.xml|package\.json|pnpm-lock\.yaml|yarn\.lock|package-lock\.json|gradle\.properties|build\.gradle(\.kts)?|settings\.gradle(\.kts)?)$/ { count++ } END { print count + 0 }')"
  code="$(printf '%s\n' "$files" | awk '/\.(java|kt|groovy|scala|ts|tsx|js|jsx|mjs|cjs|py|go|rs|rb|php|cs|cpp|cc|cxx|c|h|hpp|sql|sh|bash|zsh|yaml|yml|json|toml|xml)$/ { count++ } END { print count + 0 }')"

  if (( total == 0 )); then
    echo "chore"
  elif (( docs == total )); then
    echo "docs"
  elif (( tests == total )); then
    echo "test"
  elif (( config > 0 && code == 0 )); then
    echo "chore"
  elif (( code > 0 )); then
    echo "feat"
  else
    echo "chore"
  fi
}

default_commit_subject_for_type() {
  local type="${1:-chore}"
  case "$type" in
    docs)
      echo "update documentation"
      ;;
    test)
      echo "add or update tests"
      ;;
    chore)
      echo "update tooling and workflow"
      ;;
    feat)
      echo "update staged changes"
      ;;
    fix)
      echo "fix staged changes"
      ;;
    refactor)
      echo "refactor staged changes"
      ;;
    *)
      echo "update staged changes"
      ;;
  esac
}

resolve_project_script() {
  local project_root
  project_root="$(git_repo_root)"

  local candidate=""
  for candidate in "$@"; do
    if [[ -x "$project_root/$candidate" ]]; then
      printf '%s/%s' "$project_root" "$candidate"
      return 0
    fi
  done

  return 1
}

run_optional_project_hook() {
  local hook_name="${1:-}"
  local project_root
  project_root="$(git_repo_root)"

  if [[ -z "$hook_name" ]]; then
    return 1
  fi

  if [[ -x "$project_root/.githooks/$hook_name" ]]; then
    "$project_root/.githooks/$hook_name"
    return 0
  fi

  return 1
}
