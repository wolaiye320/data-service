#!/usr/bin/env bash
# Git 提交前一键检查
# 用法:
#   ./bin/git-check.sh [options] [path...]
#   ./bin/git-check.sh --push-gate              # review + pre-commit + pre-push gate（含单元测试）
#   ./bin/git-check.sh --review-only            # 仅输出审阅摘要
#   ./bin/git-check.sh ./bin                    # 只检查 ./bin 目录下的暂存文件
#   ./bin/git-check.sh --push-gate ./bin ./scripts

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"
source "$PROJECT_ROOT/bin/lib/git-whitespace-filter.sh"

RUN_REVIEW=true
RUN_PRE_COMMIT=true
RUN_PRE_PUSH=false
CHECK_PATHS=()

for arg in "$@"; do
  case "$arg" in
    --push-gate)
      RUN_PRE_PUSH=true
      ;;
    --review-only)
      RUN_PRE_COMMIT=false
      RUN_PRE_PUSH=false
      ;;
    -h|--help)
      cat <<'EOF'
用法: ./bin/git-check.sh [选项] [path...]

选项:
  --push-gate    附加执行 pre-push 级别检查（可能较慢，含单元测试门禁）
  --review-only  仅输出审阅摘要，不执行 gate
  -h, --help     显示帮助

参数:
  path...        指定要检查的目录或文件路径（只检查这些路径下的暂存文件）

示例:
  ./bin/git-check.sh                        # 检查所有暂存文件
  ./bin/git-check.sh ./bin                  # 只检查 ./bin 下的暂存文件
  ./bin/git-check.sh --push-gate ./src      # 执行完整检查，但只检查 ./src 下的文件
EOF
      exit 0
      ;;
    -*)
      echo "未知参数: $arg"
      echo "使用 --help 查看用法"
      exit 1
      ;;
    *)
      CHECK_PATHS+=("$arg")
      ;;
  esac
done

# 规范化路径（移除 ./ 前缀和末尾的 /）
normalize_path() {
  local path="$1"
  if [[ "$path" == "." || "$path" == "./" ]]; then
    echo "__ALL__"
    return
  fi
  # 移除 ./ 前缀
  path="${path#./}"
  # 移除末尾的 /
  path="${path%/}"
  # "./" 这类输入归一化后为空，按“检查全部路径”处理
  if [[ -z "$path" ]]; then
    echo "__ALL__"
    return
  fi
  echo "$path"
}

# 获取要检查的文件列表（根据指定的 paths 过滤）
get_staged_files() {
  if [[ ${#CHECK_PATHS[@]} -eq 0 ]]; then
    # 没有指定 paths，返回所有暂存文件
    git diff --cached --name-only --diff-filter=ACMR -z 2>/dev/null | tr '\0' '\n'
  else
    # 根据 paths 过滤暂存文件
    local filtered=""
    while IFS= read -r -d '' file; do
      for path in "${CHECK_PATHS[@]}"; do
        # 规范化路径
        local clean_path
        clean_path=$(normalize_path "$path")
        if [[ "$clean_path" == "__ALL__" ]]; then
          filtered="${filtered}${file}\n"
          break
        fi
        if [[ "$file" == "$clean_path" || "$file" == "$clean_path"/* ]]; then
          filtered="${filtered}${file}\n"
          break
        fi
      done
    done < <(git diff --cached --name-only --diff-filter=ACMR -z 2>/dev/null)
    printf '%b' "$filtered"
  fi
}

print_review_summary() {
  echo "--- 工作区状态 ---"
  git status --short || true
  echo

  if ! git diff --cached --quiet; then
    echo "--- 暂存 diffstat ---"
    git diff --cached --stat
    echo
  fi

  if ! git diff --quiet; then
    echo "--- 未暂存 diffstat ---"
    git diff --stat
    echo
  fi
}

# 检查是否有匹配的文件需要检查
STAGED_FILES=$(get_staged_files | grep -v '^$' || true)
if [[ -z "$STAGED_FILES" ]]; then
  echo "=== Git Check ==="
  echo
  echo "没有需要检查的暂存文件"
  if [[ ${#CHECK_PATHS[@]} -gt 0 ]]; then
    echo "（指定的路径: ${CHECK_PATHS[*]}）"
  fi
  exit 0
fi

echo "=== Git Check ==="

if [[ "$RUN_REVIEW" == "true" ]]; then
  echo
  echo "git-check[1/3] 审阅摘要"
  if [[ ${#CHECK_PATHS[@]} -gt 0 ]]; then
    echo "检查范围: ${CHECK_PATHS[*]}"
    echo
    echo "--- 匹配的暂存文件 ---"
    printf '%s\n' "$STAGED_FILES"
    echo
    echo "--- 暂存 diffstat ---"
    # 使用 -- 来分隔选项和路径
    git diff --cached --stat -- $(printf '%s\n' "$STAGED_FILES" | tr '\n' ' ')
    echo
  else
    print_review_summary
  fi
fi

if [[ "$RUN_PRE_COMMIT" == "true" ]]; then
  echo
  echo "git-check[2/3] pre-commit gate"
  if [[ ${#CHECK_PATHS[@]} -gt 0 ]]; then
    echo "检查范围: ${CHECK_PATHS[*]}"
    echo
    # 读取暂存文件到数组
    staged_files_filtered=()
    while IFS= read -r file; do
      [[ -n "$file" ]] && staged_files_filtered+=("$file")
    done <<< "$STAGED_FILES"

    if [[ ${#staged_files_filtered[@]} -eq 0 ]]; then
      echo "[pre-commit] 没有匹配的文件需要检查"
      exit 0
    fi

    echo "[pre-commit] validating staged changes..."

    # 1) Block likely secret files by filename.
    for file in "${staged_files_filtered[@]}"; do
      if [[ "$file" =~ (^|/)\.env(\..*)?$ ]] || \
         [[ "$file" =~ \.pem$ ]] || \
         [[ "$file" =~ \.key$ ]] || \
         [[ "$file" =~ (^|/)id_rsa(\.pub)?$ ]] || \
         [[ "$file" =~ (^|/)credentials(\.json|\.ya?ml|\.txt)?$ ]]; then
        echo "[pre-commit] blocked potential secret file: $file"
        echo "Remove it from commit, then retry."
        exit 1
      fi
    done

    # 2) Reject unresolved conflict markers only.
    #    NOTE: trailing whitespace / blank line at EOF are intentionally ignored to reduce noise.
    #    只检查指定的文件
    check_errors="$(git diff --cached --check -- $(printf '%s\n' "${staged_files_filtered[@]}" | tr '\n' ' ') 2>&1 || true)"
    conflict_errors=""
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      if [[ "$line" == *"conflict marker"* ]]; then
        conflict_errors+="$line"$'\n'
      fi
    done <<< "$check_errors"

    if [[ -n "${conflict_errors//$'\n'/}" ]]; then
      echo "[pre-commit] staged patch has unresolved conflict markers."
      printf '%s' "$conflict_errors"
      exit 1
    fi

    # 3) Enforce docs/*.md filename rules (spaces/_/parentheses/em-dash).
    if [[ -x "$PROJECT_ROOT/bin/git-doc-naming-check.sh" ]]; then
      "$PROJECT_ROOT/bin/git-doc-naming-check.sh" --staged
    fi

    # 4) Block unusually large staged files to keep commits reviewable.
    #    NOTE: docs/design/figma and common binary assets are allowed (design exports are often >1MiB).
    max_bytes=$((1024 * 1024)) # 1 MiB
    for file in "${staged_files_filtered[@]}"; do
      case "$file" in
        docs/design/figma/*) continue ;;
        docs/assets/*) continue ;;
        *.png|*.jpg|*.jpeg|*.gif|*.webp|*.pdf) continue ;;
      esac

      size="$(git cat-file -s ":$file" 2>/dev/null || echo 0)"
      if [[ "$size" =~ ^[0-9]+$ ]] && (( size > max_bytes )); then
        echo "[pre-commit] blocked large file: $file (${size} bytes > ${max_bytes})."
        echo "If required, split or handle it outside regular source commit."
        exit 1
      fi
    done

    echo "[pre-commit] passed."
  else
    if [[ -x "$PROJECT_ROOT/.githooks/pre-commit" ]]; then
      "$PROJECT_ROOT/.githooks/pre-commit"
    else
      echo "[pre-commit] 未找到 .githooks/pre-commit，跳过。"
    fi
  fi
fi

if [[ "$RUN_PRE_PUSH" == "true" ]]; then
  echo
  echo "git-check[3/3] pre-push gate"
  if [[ -x "$PROJECT_ROOT/.githooks/pre-push" ]]; then
    "$PROJECT_ROOT/.githooks/pre-push"
  else
    echo "[pre-push] 未找到 .githooks/pre-push，跳过。"
  fi
fi

echo
echo "=== Git Check Passed ==="
