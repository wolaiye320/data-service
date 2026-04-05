#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(git rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"

source "$SCRIPT_DIR/lib/git-common.sh"

scope=""
type=""
subject=""
draft_file=""

usage() {
  cat <<'EOF'
用法:
  ./bin/git/git-draft-commit-message.sh
  ./bin/git/git-draft-commit-message.sh --scope repo --type chore --subject "update helper scripts"
  ./bin/git/git-draft-commit-message.sh --draft-file .git/ALT_COMMIT_MSG

选项:
  --scope <value>       手动指定 commit scope
  --type <value>        手动指定 commit type
  --subject <value>     手动指定 commit subject
  --draft-file <path>   指定草稿文件路径，默认 .git/COMMIT_DRAFT_MSG
  -h, --help            显示帮助
EOF
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --scope)
      scope="${2:-}"
      shift 2
      ;;
    --type)
      type="${2:-}"
      shift 2
      ;;
    --subject)
      subject="${2:-}"
      shift 2
      ;;
    --draft-file)
      draft_file="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "未知参数: $1"
      usage
      exit 1
      ;;
  esac
done

staged_files="$(print_staged_files_array)"
if [[ -z "$staged_files" ]]; then
  echo "[git-draft] no staged changes. run ./bin/git/git-stage.sh first."
  exit 1
fi

if [[ -z "$scope" ]]; then
  scope="$(infer_commit_scope_from_files "$staged_files")"
fi
scope="$(sanitize_commit_scope "$scope")"

if [[ -z "$type" ]]; then
  type="$(infer_commit_type_from_files "$staged_files")"
fi

if [[ -z "$subject" ]]; then
  subject="$(default_commit_subject_for_type "$type")"
fi

if [[ -z "$draft_file" ]]; then
  draft_file="$(git_draft_file_path)"
fi

{
  echo "${type}(${scope}): ${subject}"
  echo
  echo "# bullet suggestions (edit before commit if needed)"
  printf '%s\n' "$staged_files" | sed 's#^#- update #' | head -n 8
} > "$draft_file"

echo "[git-draft] draft created: ${draft_file}"
echo
cat "$draft_file"
