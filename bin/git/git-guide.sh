#!/usr/bin/env bash
set -euo pipefail

cat <<'EOF'
Git 提交流程：

  1) ./bin/git/git-review.sh
     - 审阅当前仓库状态、暂存区和相对基线的改动

  2) ./bin/git/git-stage.sh <path...>
     - 将本次提交范围加入暂存区
     - 默认会先清空现有暂存区，再暂存指定路径
     - 追加暂存请改用: ./bin/git/git-stage.sh --append <path...>

  3) ./bin/git/git-prepare-commit.sh
     - 执行审阅 + pre-commit 检查，并生成提交草稿
     - 可选参数:
       ./bin/git/git-prepare-commit.sh --scope repo --type chore --subject "update git scripts"

  4) ./bin/git/git-commit-staged.sh --from-draft
     - 使用草稿提交
     - 或手动指定:
       ./bin/git/git-commit-staged.sh -m "type(scope): subject"

  5) ./bin/git/git-push-safe.sh
     - 先跑可选检查，再执行 git push
     - 示例:
       ./bin/git/git-push-safe.sh --remote origin --branch feature/example
       ./bin/git/git-push-safe.sh --before-push-cmd "make test"

快捷入口：
  ./bin/git/git-workflow.sh <path...>
  ./bin/git/git-workflow-auto.sh <path...>
EOF
