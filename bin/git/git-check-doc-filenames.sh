#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/git-hook.config.sh"

usage() {
  cat <<'EOF'
用法:
  ./bin/git/git-check-doc-filenames.sh --staged
  ./bin/git/git-check-doc-filenames.sh --all

检查指定文档目录下的文件名是否包含以下禁用字符:
- 空格
- 下划线 _
- 圆括号 () （）
- 长破折号 —
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
    echo "[doc-filenames] unknown mode: $mode"
    usage
    exit 2
    ;;
esac

read -r -a roots <<< "${GIT_HOOK_DOC_FILENAME_ROOTS:-docs}"
read -r -a extensions <<< "${GIT_HOOK_DOC_FILENAME_EXTENSIONS:-md}"

if [[ "${#roots[@]}" -eq 0 || -z "${roots[0]:-}" ]]; then
  exit 0
fi

matches_doc_file() {
  local path="${1:-}"
  local root=""
  local ext=""

  for root in "${roots[@]}"; do
    [[ -n "$root" ]] || continue
    if [[ "$path" == "$root/"* || "$path" == "$root" ]]; then
      for ext in "${extensions[@]}"; do
        [[ -n "$ext" ]] || continue
        if [[ "$path" == *".${ext}" ]]; then
          return 0
        fi
      done
    fi
  done

  return 1
}

files=()
if [[ "$mode" == "--staged" ]]; then
  while IFS= read -r -d '' file; do
    if matches_doc_file "$file"; then
      files+=("$file")
    fi
  done < <(git diff --cached --name-only --diff-filter=ACMR -z)
else
  for root in "${roots[@]}"; do
    [[ -d "$root" ]] || continue
    while IFS= read -r -d '' file; do
      files+=("$file")
    done < <(
      for ext in "${extensions[@]}"; do
        find "$root" -type f -name "*.${ext}" -print0 2>/dev/null || true
      done
    )
  done
fi

if [[ "${#files[@]}" -eq 0 ]]; then
  exit 0
fi

bad=0
for file in "${files[@]}"; do
  if [[ "$file" =~ [[:space:]] ]] || \
     [[ "$file" == *"_"* ]] || \
     [[ "$file" == *"("* ]] || [[ "$file" == *")"* ]] || \
     [[ "$file" == *"（"* ]] || [[ "$file" == *"）"* ]] || \
     [[ "$file" == *"—"* ]]; then
    echo "[doc-filenames] invalid filename: $file"
    echo "  forbidden: space / _ / () / （） / —"
    bad=1
  fi
done

if [[ "$bad" -ne 0 ]]; then
  echo "[doc-filenames] fix filenames then retry."
  exit 1
fi
