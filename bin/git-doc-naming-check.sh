#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bin/git-doc-naming-check.sh --staged
  bin/git-doc-naming-check.sh --all

Checks docs/**/*.md filenames for forbidden characters:
- space
- underscore _
- parentheses () （）
- em dash —
EOF
}

mode="${1:-}"
case "$mode" in
  --staged|--all) ;;
  -h|--help|"")
    usage
    exit 0
    ;;
  *)
    echo "[doc-naming] unknown mode: $mode"
    usage
    exit 2
    ;;
esac

files=()
if [[ "$mode" == "--staged" ]]; then
  while IFS= read -r -d '' file; do
    files+=("$file")
  done < <(git diff --cached --name-only --diff-filter=ACMR -z)
else
  while IFS= read -r -d '' file; do
    files+=("$file")
  done < <(find docs -type f -name '*.md' -print0 2>/dev/null || true)
fi

if [[ "${#files[@]}" -eq 0 ]]; then
  exit 0
fi

bad=0
for file in "${files[@]}"; do
  [[ "$file" == docs/* ]] || continue
  [[ "$file" == *.md ]] || continue

  if [[ "$file" =~ [[:space:]] ]] || \
     [[ "$file" == *"_"* ]] || \
     [[ "$file" == *"("* ]] || [[ "$file" == *")"* ]] || \
     [[ "$file" == *"（"* ]] || [[ "$file" == *"）"* ]] || \
     [[ "$file" == *"—"* ]]; then
    echo "[doc-naming] invalid filename: $file"
    echo "  forbidden: space / _ / () / （） / —"
    bad=1
  fi
done

if [[ "$bad" -ne 0 ]]; then
  echo "[doc-naming] fix filenames then retry."
  exit 1
fi

