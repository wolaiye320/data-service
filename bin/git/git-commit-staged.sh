#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(git rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"

source "$SCRIPT_DIR/lib/git-common.sh"

draft_file="$(git_draft_file_path)"

usage() {
  cat <<'EOF'
用法:
  ./bin/git/git-commit-staged.sh -m "type(scope): subject"
  ./bin/git/git-commit-staged.sh --from-draft
  ./bin/git/git-commit-staged.sh --from-draft --draft-file .git/ALT_COMMIT_MSG
EOF
}

if git diff --cached --quiet; then
  echo "[git-commit] no staged changes. run ./bin/git/git-stage.sh first."
  exit 1
fi

case "${1:-}" in
  --from-draft)
    shift
    if [[ "${1:-}" == "--draft-file" ]]; then
      draft_file="${2:-}"
      shift 2
    fi
    if [[ ! -f "$draft_file" ]]; then
      echo "[git-commit] draft not found: $draft_file"
      exit 1
    fi
    git commit -F "$draft_file"
    ;;
  -m)
    if [[ -z "${2:-}" ]]; then
      usage
      exit 1
    fi
    git commit -m "$2"
    ;;
  -h|--help|"")
    usage
    exit 0
    ;;
  *)
    echo "未知参数: $1"
    usage
    exit 1
    ;;
esac
