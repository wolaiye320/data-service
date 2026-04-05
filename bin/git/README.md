# bin/git 说明

`bin/git/` 存放 Git 脚本入口。

- 目标：把 Git 提交、校验、推送拆成语义清晰的小脚本
- 设计原则：通用逻辑放在 `lib/`，项目特有动作通过参数传入，不写死在脚本里

## 推荐怎么用

| 场景 | 推荐命令 | 说明 |
|---|---|---|
| 查看整体流程 | `./bin/git/git-guide.sh` | 先看一遍推荐流程和主要入口 |
| 只看仓库改动摘要 | `./bin/git/git-review.sh` | 只读，不改任何内容 |
| 暂存本次要提交的文件 | `./bin/git/git-stage.sh <path...>` | 默认先清空暂存区，再只暂存指定路径 |
| 暂存后做检查并生成提交草稿 | `./bin/git/git-prepare-commit.sh` | 常用于正式提交前 |
| 用草稿提交 | `./bin/git/git-commit-staged.sh --from-draft` | 会触发 `commit-msg` hook |
| 推送 | `./bin/git/git-push-safe.sh --remote origin --branch <branch>` | 可附加推送前命令 |
| 交互式完整流程 | `./bin/git/git-workflow.sh <path...>` | review -> stage -> check -> draft -> commit -> optional push |
| 非交互式完整流程 | `./bin/git/git-workflow-auto.sh <path...>` | 适合自动化或 AI 执行 |

## 文件用途

| 文件 | 用途 | 直接依赖 | 谁会调用它 |
|---|---|---|---|
| `git-guide.sh` | 输出推荐流程说明 | 无 | 人直接执行 |
| `git-review.sh` | 输出仓库状态、暂存区、相对基线改动摘要 | Git 命令 | 人直接执行；`git-check.sh` |
| `git-stage.sh` | 按路径暂存文件；默认替换整个暂存区，可 `--append` 追加 | Git 命令 | 人直接执行；`git-workflow.sh`；`git-workflow-auto.sh` |
| `git-check.sh` | 对暂存区做审阅和 gate 检查，可选执行 pre-push hook | `lib/git-common.sh`、`lib/git-index-guards.sh` | 人直接执行；`git-prepare-commit.sh` |
| `git-hook.config.sh` | hook 通用默认配置，负责加载项目覆盖配置 | 无 | `git-hook-commit-msg.sh`、`git-hook-pre-push.sh`、`lib/git-index-guards.sh` |
| `git-hook.project.config.sh` | 项目级参数层，集中定义路径规则、测试命令、前端构建命令 | 无 | `git-hook.config.sh` |
| `git-hook-commit-msg.sh` | 提交信息校验主体逻辑 | `git-hook.config.sh` | `.githooks/commit-msg` |
| `git-hook-pre-commit.sh` | pre-commit 主体逻辑 | `lib/git-index-guards.sh` | `.githooks/pre-commit` |
| `git-hook-pre-push.sh` | pre-push 主体逻辑，按配置执行命令和测试要求 | `git-hook.config.sh` | `.githooks/pre-push` |
| `git-check-doc-filenames.sh` | 检查配置指定目录下的文档文件名是否合法 | Git / `find` | `lib/git-index-guards.sh` |
| `git-draft-commit-message.sh` | 根据暂存文件推断 `type/scope/subject`，生成 `.git/COMMIT_DRAFT_MSG` | `lib/git-common.sh` | 人直接执行；`git-prepare-commit.sh` |
| `git-prepare-commit.sh` | 先执行 `git-check.sh`，再执行 `git-draft-commit-message.sh` | `git-check.sh`、`git-draft-commit-message.sh` | 人直接执行；`git-workflow.sh`；`git-workflow-auto.sh` |
| `git-commit-staged.sh` | 用 `-m` 或 `--from-draft` 提交暂存区 | `lib/git-common.sh` | 人直接执行；`git-workflow.sh`；`git-workflow-auto.sh` |
| `git-push-safe.sh` | 先执行可选 hook / 自定义命令，再执行 `git push` | `lib/git-common.sh` | 人直接执行；`git-workflow.sh`；`git-workflow-auto.sh` |
| `git-workflow.sh` | 交互式串联完整提交流程 | `git-review.sh`、`git-stage.sh`、`git-prepare-commit.sh`、`git-commit-staged.sh`、`git-push-safe.sh` | 人直接执行 |
| `git-workflow-auto.sh` | 非交互式串联完整提交流程 | `git-review.sh`、`git-stage.sh`、`git-prepare-commit.sh`、`git-commit-staged.sh`、`git-push-safe.sh` | 人直接执行 |
| `git-scripts-smoke-test.sh` | 在临时 Git 仓库中做冒烟验证，确认这些脚本可以跨项目复用 | `git-stage.sh`、`git-draft-commit-message.sh`、`git-commit-staged.sh`、`git-push-safe.sh`、`lib/git-common.sh` | 人工验证时执行 |
| `lib/git-common.sh` | 公共函数库：仓库根目录、草稿路径、路径过滤、scope/type 推断、可选 hook 执行 | 无 | `git-check.sh`、`git-draft-commit-message.sh`、`git-commit-staged.sh`、`git-push-safe.sh` |
| `lib/git-index-guards.sh` | 暂存区公共校验：敏感文件、冲突标记、大文件、文档命名检查 | `git-check-doc-filenames.sh`、`git-hook.config.sh` | `git-check.sh`、`git-hook-pre-commit.sh` |

## 依赖关系

### 1. 最常见的提交链路

| 顺序 | 脚本 | 作用 |
|---|---|---|
| 1 | `git-review.sh` | 先看清当前改了什么 |
| 2 | `git-stage.sh` | 只把本次要提交的文件放进暂存区 |
| 3 | `git-prepare-commit.sh` | 先检查，再生成提交草稿 |
| 4 | `git-commit-staged.sh --from-draft` | 使用草稿提交 |
| 5 | `git-push-safe.sh` | 需要时推送 |

### 2. 自动串联关系

| 脚本 | 内部调用链 |
|---|---|
| `git-workflow.sh` | `git-review.sh` → `git-stage.sh` → `git-prepare-commit.sh` → `git-commit-staged.sh` → `git-push-safe.sh` |
| `git-workflow-auto.sh` | `git-review.sh` → `git-stage.sh` → `git-prepare-commit.sh` → `git-commit-staged.sh` → `git-push-safe.sh` |
| `git-prepare-commit.sh` | `git-check.sh` → `git-draft-commit-message.sh` |
| `git-check.sh` | `lib/git-common.sh` + `lib/git-index-guards.sh`，必要时还会调 `.githooks/pre-commit` / `.githooks/pre-push` |
| `.githooks/commit-msg` | `git-hook-commit-msg.sh` |
| `.githooks/pre-commit` | `git-hook-pre-commit.sh` |
| `.githooks/pre-push` | `git-hook-pre-push.sh` |

### 3. 依赖结构图

```text
git-workflow.sh
  -> git-review.sh
  -> git-stage.sh
  -> git-prepare-commit.sh
       -> git-check.sh
            -> lib/git-common.sh
            -> lib/git-index-guards.sh
                 -> git-hook.config.sh
                 -> git-check-doc-filenames.sh
            -> .githooks/pre-commit
            -> .githooks/pre-push
       -> git-draft-commit-message.sh
            -> lib/git-common.sh
  -> git-commit-staged.sh
       -> lib/git-common.sh
  -> git-push-safe.sh
       -> lib/git-common.sh

.githooks/commit-msg
  -> git-hook-commit-msg.sh
       -> git-hook.config.sh

.githooks/pre-commit
  -> git-hook-pre-commit.sh
       -> lib/git-index-guards.sh
            -> git-hook.config.sh

.githooks/pre-push
  -> git-hook-pre-push.sh
       -> git-hook.config.sh

git-workflow-auto.sh
  -> 与 git-workflow.sh 基本相同，只是减少交互
```

## 各脚本应该在什么情况下用

| 你要做什么 | 用哪个 |
|---|---|
| 只想看现在仓库是什么状态 | `git-review.sh` |
| 已经很确定要提交哪些文件 | `git-stage.sh` |
| 想让脚本帮你先检查，再顺手生成提交信息草稿 | `git-prepare-commit.sh` |
| 已经写好或生成好提交信息，只差执行提交 | `git-commit-staged.sh` |
| 你有项目特有测试命令，想在 push 前跑一下 | `git-push-safe.sh --before-push-cmd "<cmd>"` |
| 你想把这些脚本复制到别的仓库 | 改 `git-hook.config.sh`，不要先改 hook 主体 |
| 想一步一步有人机交互引导地提交 | `git-workflow.sh` |
| 想非交互自动完成提交 | `git-workflow-auto.sh` |
| 想验证这些脚本在独立仓库里是否还能工作 | `git-scripts-smoke-test.sh` |

## 两种常用用法

### 手动、稳妥

```bash
./bin/git/git-review.sh
./bin/git/git-stage.sh bin/git/README.md
./bin/git/git-prepare-commit.sh --scope git --type docs --subject "document git script usage"
./bin/git/git-commit-staged.sh --from-draft
```

### 自动串联

```bash
./bin/git/git-workflow-auto.sh --scope git --type chore --subject "refine git scripts" bin/git
```

### 推送前带自定义命令

```bash
./bin/git/git-push-safe.sh \
  --before-push-cmd "make test" \
  --remote origin \
  --branch feature/example
```

## `lib/` 为什么单独存在

| 文件 | 为什么单独拆出来 |
|---|---|
| `lib/git-common.sh` | 多个脚本都要用到仓库根目录、草稿文件路径、scope/type 推断，如果每个脚本都写一遍会很乱 |
| `lib/git-index-guards.sh` | 暂存区检查逻辑既被 `git-check.sh` 用，也被 `git-hook-pre-commit.sh` 用，抽出来能保证规则只维护一份 |

## hook 配置怎么改

| 目标 | 改哪里 |
|---|---|
| 修改允许的 commit type | `git-hook.config.sh` 的 `GIT_HOOK_COMMIT_TYPES` |
| 修改 scope 正则 | `git-hook.config.sh` 的 `GIT_HOOK_SCOPE_REGEX` |
| 修改默认比较基线候选 | `git-hook.config.sh` 的 `GIT_HOOK_BASE_REF_CANDIDATES` |
| 修改大文件阈值 | `git-hook.config.sh` 的 `GIT_HOOK_MAX_FILE_SIZE_BYTES` |
| 修改大文件白名单 | `git-hook.config.sh` 的 `GIT_HOOK_LARGE_FILE_ALLOW_PATTERNS` |
| 修改文档命名检查目录和扩展名 | `git-hook.config.sh` 的 `GIT_HOOK_DOC_FILENAME_ROOTS`、`GIT_HOOK_DOC_FILENAME_EXTENSIONS` |
| 修改 pre-push 比较基线 | `git-hook.config.sh` 的 `GIT_HOOK_BASE_REF` |
| 修改路径匹配后的验证命令 | `git-hook.config.sh` 的 `GIT_HOOK_PATH_COMMANDS` |
| 修改“代码改动必须带测试改动”的规则 | `git-hook.config.sh` 的 `GIT_HOOK_REQUIRED_TEST_RULES` |

## 配置分层

| 文件 | 用途 |
|---|---|
| `git-hook.config.sh` | 放通用默认值，也负责加载项目覆盖配置 |
| `git-hook.project.config.sh` | 放当前仓库的参数，如模块列表、路径模式、测试命令和构建命令 |

## 项目参数优先改哪里

| 目标 | 参数 |
|---|---|
| 后端模块列表 | `GIT_HOOK_BACKEND_MODULES` |
| 哪些路径触发模块测试 | `GIT_HOOK_BACKEND_MODULE_PATTERNS` |
| 模块测试命令模板 | `GIT_HOOK_BACKEND_TEST_COMMAND_TEMPLATE` |
| 根目录文件触发全量测试 | `GIT_HOOK_ROOT_TEST_TRIGGER_PATTERNS`、`GIT_HOOK_ROOT_TEST_COMMAND` |
| 哪些路径触发前端构建 | `GIT_HOOK_FRONTEND_PATH_PATTERNS`、`GIT_HOOK_FRONTEND_BUILD_COMMAND` |
| 哪些代码路径必须带测试 | `GIT_HOOK_REQUIRED_TEST_CODE_PATTERNS`、`GIT_HOOK_REQUIRED_TEST_PATH_TEMPLATE`、`GIT_HOOK_REQUIRED_TEST_MESSAGE` |

如果只记一件事：

- 人工提交优先用 `git-workflow.sh`
- 自动化提交优先用 `git-workflow-auto.sh`
- 需要理解细节时，从 `git-guide.sh` 和本 README 开始看
