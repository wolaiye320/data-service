#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(git -C "$SCRIPT_DIR/../.." rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"
source "$PROJECT_ROOT/bin/git/lib/git-common.sh"
source "$PROJECT_ROOT/bin/git/lib/git-index-guards.sh"

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
用法: ./bin/git/git-check.sh [选项] [path...]

选项:
  --push-gate    附加执行 pre-push 级别检查
  --review-only  仅输出审阅摘要，不执行 gate
  -h, --help     显示帮助

参数:
  path...        指定要检查的目录或文件路径（只检查这些路径下的暂存文件）

示例:
  ./bin/git/git-check.sh                        # 检查所有暂存文件
  ./bin/git/git-check.sh ./bin                  # 只检查 ./bin 下的暂存文件
  ./bin/git/git-check.sh --push-gate ./src      # 执行完整检查，但只检查 ./src 下的文件
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

if [[ "${#CHECK_PATHS[@]}" -eq 0 ]]; then
  STAGED_FILES="$(print_matching_staged_files | awk 'NF')"
else
  STAGED_FILES="$(print_matching_staged_files "${CHECK_PATHS[@]}" | awk 'NF')"
fi
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
    ./bin/git/git-review.sh
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

    run_common_pre_commit_checks "$PROJECT_ROOT" "${staged_files_filtered[@]}"
  else
    run_optional_project_hook pre-commit || true
  fi
fi

if [[ "$RUN_PRE_PUSH" == "true" ]]; then
  echo
  echo "git-check[3/3] pre-push gate"
  run_optional_project_hook pre-push || true
fi

echo
echo "=== Git Check Passed ==="
