# Git 与协作规范

## 1. 基本原则

- 所有开发与普通缺陷修复默认在 `feature/*` 分支进行
- `main` 为生产就绪分支，受保护，不应直接在其上持续开发
- 一个 PR 只聚焦一个主题，避免混入无关改动
- 未经明确要求，Agent 不得直接执行合并到 `main` 的操作

## 2. 提交规范

提交模板：

```text
<type>(<scope>): <subject>
```

要求：

1. 冒号后必须有一个空格
2. `subject` 使用简洁祈使句，聚焦“做了什么”
3. 如有多项关键变更，在提交正文补充要点列表，使用 `-` 开头

`type` 枚举：

- `feat`：新增功能
- `fix`：修复缺陷
- `docs`：文档变更
- `style`：代码格式调整，不影响运行
- `refactor`：重构，非新增功能且非缺陷修复
- `perf`：性能优化
- `test`：测试相关
- `chore`：构建、工具、流程维护
- `revert`：回滚提交
- `build`：构建与打包变更

示例：

```text
feat(web): add quality tickets page

- add tickets list query and pagination
- add status filter and assignee filter
- add basic empty-state handling
```

## 3. 分支模型

- `main`：生产就绪分支，长期存在，受保护
- `feature/*`：日常功能与一般缺陷修复分支，来源 `main`，回合并 `main`
- `hotfix/*`：线上紧急修复分支，来源 `main`，回合并 `main`
- `release/*`：按需创建的发布准备分支，来源 `main`，回合并 `main`

命名约定：

| 分支类型 | 命名格式 | 示例 |
| --- | --- | --- |
| 功能分支 | `feature/[描述]` | `feature/quality-ticket-api` |
| 修复分支 | `feature/[描述]` | `feature/fix-login-timeout` |
| 热修复分支 | `hotfix/vX.Y.Z-[描述]` | `hotfix/v1.2.1-auth-null-check` |
| 发布分支 | `release/vX.Y.Z` | `release/v1.3.0` |

补充说明：

- 日常开发与普通缺陷修复统一使用 `feature/*`
- 不再使用 `bugfix/*` 或 `fix/*` 作为分支前缀，避免分支模型混乱

## 4. Pull Request 规则

1. 所有变更必须通过 PR 合并
2. 至少 1 个 Reviewer 批准
3. CI / 检查项必须通过
4. 合并前分支需与目标分支保持最新
5. 合并后删除源分支，保留审计记录即可

## 5. 合并策略

- 默认使用 `Squash and merge`
- 保持 `main` 历史整洁
- 严禁 `force push` 到受保护分支

## 6. Agent 主动提示规则

- 当用户进入开发实现阶段且当前分支为 `main` 时，Agent 必须先提示创建功能分支，建议格式：`feature/[desc]`
- 若检测到在 `main` 上存在工作区改动，Agent 必须优先提示切换到 `feature` 分支后继续
- 当用户表达“完成开发”“准备提测”“准备上线”“准备合并”时，Agent 必须主动提示以下合并前置流程：
  1. 同步目标分支最新代码
  2. 执行本地检查与测试，包含 pre-commit / pre-push gate
  3. 推送 `feature` 分支并创建 PR
  4. 审核通过后再合并到 `main`

## 7. 本地 Git Hooks

首次启用：

```bash
git config core.hooksPath .githooks
chmod +x .githooks/* bin/git-review.sh
```

执行时机：

- `commit-msg`：校验提交信息格式
- `pre-commit`：校验暂存区，包含敏感文件、冲突标记、超大文件
- `pre-push`：按改动范围执行最小验证，如后端模块单元测试、前端 build
