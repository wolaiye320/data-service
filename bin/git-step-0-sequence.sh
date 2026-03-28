#!/usr/bin/env bash
# 显示 Git 提交推荐顺序（编号版）

set -euo pipefail

cat <<'EOF'
Git 提交流程（编号版）：

  0) ./bin/git-step-0-sequence.sh
     - 查看完整步骤说明

  1) ./bin/git-step-1-review.sh
     - 审阅工作区改动（只读）

  2) ./bin/git-step-2-stage.sh <path...>
     - 将本次提交范围加入暂存区
     - 示例: ./bin/git-step-2-stage.sh AGENTS.md bin/git-check.sh

  3) ./bin/git-step-3-check.sh
     - 审阅暂存区、执行 pre-commit gate，并生成提交草稿

  4) ./bin/git-step-4-commit.sh --from-draft
     - 使用草稿提交（自动触发 commit-msg 校验）
     - 或: ./bin/git-step-4-commit.sh -m "type(scope): subject"

  5) ./bin/git-step-5-push.sh [remote] [branch]
     - 推送前执行 pre-push gate，再 git push
     - 示例: ./bin/git-step-5-push.sh origin feature/123-update-bin-scripts

快捷模式：
  ./bin/git-step-all.sh <path...>
    - 串联 1 -> 5（含提交后是否 push 的交互引导）
    - 默认不强制 push，由你确认后执行

前置一次性设置：
  git config core.hooksPath .githooks
EOF
