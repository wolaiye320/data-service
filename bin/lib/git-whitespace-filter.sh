#!/usr/bin/env bash

# 过滤 git diff --check 的输出：
# - 允许 *.md 文件中的 trailing whitespace；
# - 保留其他 whitespace 与 conflict marker 错误。
filter_git_check_output_allow_md_trailing_whitespace() {
  local check_errors="${1:-}"
  local filtered_errors=""
  local skip_next_context_line=0
  local line=""
  local file=""

  if [[ -z "$check_errors" ]]; then
    printf '%s' "$filtered_errors"
    return 0
  fi

  while IFS= read -r line; do
    [[ -z "$line" ]] && continue

    if (( skip_next_context_line == 1 )); then
      skip_next_context_line=0
      if [[ "$line" == "+"* ]]; then
        continue
      fi
    fi

    if [[ "$line" == *": trailing whitespace."* ]]; then
      file="${line%%:*}"
      if [[ "$file" == *.md ]]; then
        skip_next_context_line=1
        continue
      fi
    fi

    filtered_errors+="$line"$'\n'
  done <<< "$check_errors"

  printf '%s' "$filtered_errors"
}
