# 数据服务 项目指南

项目规范和开发指南。

## 响应语言与风格

- 始终使用中文回复用户，技术专有名词可保留英文（如 API、Python、DTO 等）
- 尽可能少地输出内容，仅提供高信息密度回复，禁止无效寒暄、过度铺垫，只输出对当前任务有直接帮助的信息
- 使用项目既有风格，不引入新风格，包括代码、文档或交互，默认延用项目中已有的格式、缩进、命名习惯

## 项目结构与通用规范

### 项目结构规则

- **分层组织**：按功能或领域划分目录，遵循"关注点分离"原则
- **命名一致**：使用一致且描述性的目录和文件命名，反映其用途和内容
- **模块化**：相关功能放在同一模块，减少跨模块依赖
- **适当嵌套**：避免过深的目录嵌套，一般不超过 3-4 层
- **资源分类**：区分代码、资源、配置和测试文件
- **依赖管理**：集中管理依赖，避免多处声明

### 通用开发原则

- **可测试性**：编写可测试的代码，组件应保持单一职责
- **DRY 原则**：避免重复代码，提取共用逻辑到单独的函数或类
- **代码简洁**：保持代码简洁明了，遵循 KISS 原则（保持简单直接）
- **命名规范**：使用描述性的变量、函数和类名，反映其用途和含义
- **注释文档**：为复杂逻辑添加注释，编写清晰的文档说明功能和用法
- **风格一致**：遵循项目或语言的官方风格指南和代码约定
- **技术栈选择**：非必要不要引入新的组件工具，尤其有传染性的开源组件，复杂的功能或算法需要引入时，优先使用中国开发的成熟的库和工具
- **架构设计**：考虑代码的可维护性、可扩展性和性能需求
- **版本控制**：编写有意义的提交信息，保持逻辑相关的更改在同一提交中
- **异常处理**：正确处理边缘情况和错误，提供有用的错误信息
- **代码安全**：不得提供不安全代码（如 SQL 注入、硬编码密钥、未校验输入等），涉及安全场景时遵循 security-review 技能
- **性能意识**：不得忽视性能问题，在涉及数据量、并发、IO 等场景应主动评估与优化
- **错误处理**：不得跳过错误处理，外部调用、边界条件、异常路径须有明确的处理逻辑
- **技术选型**：不得使用已弃用或过时的技术，遵循项目技术栈版本约束

## 技术栈

待补充

## Skills 使用规范

- **编写或修改前端界面时**，必须先读取 `.claude/skills/frontend-design/SKILL.md`，按其规范执行
- **编写测试代码时**，必须先读取 `.claude/skills/webapp-testing/SKILL.md`，按其规范执行
- **创建或配置 MCP Server 时**，必须先读取 `.claude/skills/mcp-builder/SKILL.md`，按其规范执行
- **生成 docx / pdf / pptx / xlsx 文件时**，必须先读取对应目录下的 SKILL.md：
  - `.claude/skills/docx/SKILL.md`
  - `.claude/skills/pdf/SKILL.md`
  - `.claude/skills/pptx/SKILL.md`
  - `.claude/skills/xlsx/SKILL.md`
- **当用户指出当前做法不合适并给出明确要求时**，将该规范追加写入 `.claude/skills/learned/SKILL.md`
- **处理任何任务前**，必须读取 `.claude/skills/learned/SKILL.md`，按其中积累的规范执行


## Java 语言规范

### 命名约定

- **类名**：使用帕斯卡命名法（如 `UserController`、`OrderService`）
- **方法和变量名**：使用驼峰命名法（如 `findUserById`、`isOrderValid`）
- **常量**：使用全大写下划线分隔（如 `MAX_RETRY_ATTEMPTS`、`DEFAULT_PAGE_SIZE`）
- **包名**：使用小写，按功能模块划分，统一使用 `cn.dtkeys` 前缀（如 `cn.dtkeys.linkinsight.user.domain`）

### 代码风格

- **缩进**：使用 4 个空格，不使用 Tab
- **行长度**：每行不超过 120 个字符
- **大括号**：使用 Egyptian 风格（开括号不换行）
- **空行**：方法间使用一个空行分隔，逻辑块间使用空行分隔

### 异常处理

- **检查异常**：谨慎使用检查异常，优先使用运行时异常
- **异常链**：保持异常链，不丢失原始异常信息
- **资源管理**：使用 try-with-resources 自动管理资源

### 集合和流处理

- **集合选择**：根据使用场景选择合适的集合类型
  - `ArrayList`：随机访问频繁
  - `LinkedList`：插入删除频繁
  - `HashMap`：键值对存储
  - `TreeMap`：需要排序的键值对
- **Stream API**：充分利用 Stream API 进行函数式编程

### 并发编程

- **线程安全**：优先使用不可变对象和线程安全的集合
- **锁机制**：合理使用 synchronized、ReentrantLock 等锁机制
- **并发集合**：使用 ConcurrentHashMap、CopyOnWriteArrayList 等并发集合
- **CompletableFuture**：使用 CompletableFuture 处理异步操作

### 文档和注释

- **JavaDoc**：为公共 API（Controller、Service、Repository 接口）编写完整 JavaDoc
- **代码注释**：为复杂逻辑添加解释性注释，避免对自解释代码过度注释
- **TODO 标记**：使用 TODO 标记待完成的工作

### 测试规范

测试规范以以下 3 份文档为唯一准入来源，禁止在 `AGENTS.md` 维护重复摘要，避免口径漂移：

- 核心规范：`docs/research/common/测试规范.md`
- 代码示例：`docs/research/common/测试规范-代码示例.md`
- 高级主题：`docs/research/common/测试规范-高级主题.md`

执行规则：

- 默认先阅读核心规范；需要示例时再看代码示例；需要处理隔离、异构数据库、缓存、E2E 稳定性等复杂问题时再看高级主题
- 所有测试要求、覆盖率、分层、Mock 规则、E2E 规则，统一以这 3 份文档为准
- 修改测试策略时，直接更新这 3 份文档，不在 `AGENTS.md` 增补平行规则

### 全栈开发完整性（强制）

- **新增 API 或业务功能时**：必须同时开发并提交对应前端界面，禁止仅完成后端而遗漏前端。
- **例外**：纯内部 API（无用户界面）、批处理、定时任务、Webhook 回调等不对外展示的能力可仅实现后端。
- **AI 主动提示**：当检测到新增或修改 Controller/API 且无对应 `xxx-web` 变动时，必须主动提示“是否需要同步开发前端界面？”。

### 数据库迁移规范（Flyway）

- 迁移文件命名：`V{N}__{description}.sql`，如 `V45__relation_candidate.sql`
- 文件位置：`src/main/resources/db/migration/`
- 版本号 `N` 必须单调递增，不可重复或跳号后回填
- 每次 Schema 变更（建表、加字段、加索引）必须新建迁移文件，禁止修改已提交的迁移文件
- 新增迁移文件必须配套集成测试验证字段映射正确性

### MyBatis 使用规范

- **禁止使用 XML Mapper 文件**：所有 Repository 接口必须使用注解方式（`@Select`、`@Insert`、`@Update`、`@Delete`）定义 SQL，禁止使用 XML Mapper 文件
- **原因**：当模块被打包成 JAR 并被其他模块依赖时，MyBatis 无法可靠地扫描到依赖 JAR 中的 XML Mapper 文件，导致运行时绑定异常
- **注解使用**：
  - 简单查询：使用 `@Select` 注解直接定义 SQL
  - 插入操作：使用 `@Insert` 配合 `@Options(useGeneratedKeys = true, keyProperty = "id")` 自动获取主键
  - 更新操作：使用 `@Update` 注解
  - 删除操作：使用 `@Delete` 注解
  - 结果映射：使用 `@Results` 和 `@Result` 定义字段映射，使用 `@ResultMap` 引用已定义的映射
- **SQL 格式**：对于复杂 SQL，使用 Java Text Blocks（三引号）提高可读性
- **参数绑定**：使用 `@Param` 注解明确指定参数名称


## 项目环境规则

### 服务端口规范

- **后端**：8081
- **前端**：3001

### 环境拓扑与命名映射

- 系统元数据库对应的 MCP 为 `linkinsight`
- 业务数据库对应 MCP `bankdb` 下的 `public` schema
- 业务数据库 `bankdb` 转换成的数据集市为 `datamart`

### 默认作用域（自动化任务默认使用）

- 未明确指定时，业务库查询默认作用域为 `bankdb.public`
- 未明确指定时，系统元数据操作默认走 `linkinsight`
- 涉及数据集市的任务默认目标为 `datamart`



## Git 规范

### 重要原则

待补充

### 提交规范

提交模板：`<type>(<scope>): <subject>`

要求：
1. 冒号后必须有一个空格
2. `subject` 使用简洁祈使句，聚焦"做了什么"
3. 如有多项关键变更，在提交正文补充要点列表（`-` 开头）

`type` 枚举：
- `feat`: 新增功能
- `fix`: 修复缺陷
- `docs`: 文档变更
- `style`: 代码格式调整（不影响运行）
- `refactor`: 重构（非新增功能、非缺陷修复）
- `perf`: 性能优化
- `test`: 测试相关
- `chore`: 构建/工具/流程维护
- `revert`: 回滚提交
- `build`: 构建与打包变更

示例：
```
feat(web): add quality tickets page

- add tickets list query and pagination
- add status filter and assignee filter
- add basic empty-state handling
```

### 分支模型

- `main`：生产就绪分支（长期存在，受保护）
- `feature/*`：日常功能与一般修复分支，来源 `main`，回合并 `main`
- `hotfix/*`：线上紧急修复分支，来源 `main`，回合并 `main`
- `release/*`：按需创建的发布准备分支（可选），来源 `main`，回合并 `main`

命名约定：

| 分支类型   | 命名格式                    | 示例                          |
| ---------- | --------------------------- | ----------------------------- |
| 功能分支   | `feature/[issue-id]-[描述]` | `feature/123-quality-ticket-api` |
| 修复分支   | `feature/[issue-id]-[描述]` | `feature/456-fix-login-timeout`  |
| 热修复分支 | `hotfix/vX.Y.Z-[描述]`      | `hotfix/v1.2.1-auth-null-check`  |
| 发布分支   | `release/vX.Y.Z`            | `release/v1.3.0`                 |

说明：
- 统一使用 `feature/*` 承载日常开发与普通缺陷修复，不再使用 `bugfix/*` / `fix/*` 作为分支前缀，避免冲突

### Pull Request 规则

1. 所有变更必须通过 PR 合并
2. 至少 1 个 Reviewer 批准
3. CI/检查项必须通过
4. 合并前分支需与目标分支保持最新
5. 合并后删除源分支（保留审计记录即可）

### 合并策略

- 默认使用 `Squash and merge`，保持 `main` 历史整洁
- 一个 PR 聚焦一个主题，避免混入无关改动
- 严禁 `force push` 到受保护分支

### AI 主动提示规则（强制）

- 当用户进入开发实现阶段且当前分支为 `main` 时，AI 必须先提示创建功能分支（建议：`feature/[issue-id]-[desc]`）
- 当用户表达"完成开发/准备提测/准备上线/准备合并"时，AI 必须主动提示合并前置流程：
  1. 同步目标分支最新代码
  2. 执行本地检查与测试（含 pre-commit / pre-push gate）
  3. 推送 feature 分支并创建 PR
  4. 审核通过后再合并到 `main`
- 若检测到在 `main` 上存在工作区改动，AI 必须优先提示切换到 feature 分支后继续
- 在用户未明确要求时，AI 不得直接执行合并到 `main` 的操作

### 本地 Git Hooks 配置

首次启用（本地仓库执行一次）：
```bash
git config core.hooksPath .githooks
chmod +x .githooks/* scripts/git-review.sh
```

执行时机：
- `commit-msg`：校验提交信息格式（Conventional Commits）
- `pre-commit`：校验暂存区（敏感文件、冲突标记、超大文件）
- `pre-push`：按改动范围执行最小验证（后端模块单元测试 / 前端 build）

## 研究文档组织规范（强制）

- **需求规格**：新需求、子需求、细化条款均在现有「需求规格说明书」中补充或新增章节，不新建零散需求文档。
- **架构与详细设计**：新方案、子方案、实施要点均在现有「架构与详细设计」中补充或新增章节，不新建独立的实施方案、设计说明等零碎文档。
- **例外**：全新业务模块（如新增独立 WBS 一级模块）可新建对应需求与架构文档；跨模块通用规范（如多租户、安全基线）可独立成文，但应在各模块文档中引用并保持同步。

## 相关文档

- 详细技术选型依据：`docs/research/common/技术选型说明书.md`
- 前端 UI 设计规范：`docs/research/common/UI设计规范.md`
- 前端开发规范：`docs/research/common/前端开发规范.md`
- 项目规则：`.cursor/rules/` 目录

