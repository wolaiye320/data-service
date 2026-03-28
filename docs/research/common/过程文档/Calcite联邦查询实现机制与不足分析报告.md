# Calcite 联邦查询实现机制与不足分析报告

## 1. 文档目的

本文档基于 Apache Calcite 1.41.0 源码目录 `/Volumes/osdisk/java_code/01-open_source/calcite-calcite-1.41.0`，分析 Calcite 当前是如何实现跨数据源联邦查询的，以及其在工程落地中的主要不足，为数据服务系统第二阶段基于 Calcite 的联邦 SQL 方案提供实现依据和边界判断。

本文不基于二手材料推测，结论均以 Calcite 1.41.0 源码、官方文档和官方测试用例为依据。

## 2. 结论摘要

Calcite 现在确实能够做联邦查询，但它的实现方式要准确理解为：

- 多数据源统一挂载
- 统一 SQL 解析、校验、关系代数化
- 基于各 adapter 的 convention 和 rule 做局部下推
- 不能继续下推的部分回退到本地执行层

因此，Calcite 的联邦查询本质是：

- 强规划
- 弱执行
- 适合作为联邦 SQL 内核
- 不等于成品级联邦查询平台

最关键的判断有四条：

1. Calcite 已经具备多 schema / 多外部数据源统一查询能力。
2. Calcite 的下推是“按 adapter convention 推进”的，不是跨所有数据源自动形成统一远端执行。
3. 跨不同数据源时，很多场景最终会退回到 `Enumerable` 本地执行层完成 join、聚合、结果整合。
4. Calcite 自身没有把联邦执行平台、服务化、权限、安全、持久化仓库这些产品层能力做完整。

所以，对本项目来说，Calcite 适合作为第二阶段的：

- Parser
- Validator
- RelNode Planner
- Rule-based Optimizer
- Join Reorder / Pushdown Candidate 识别核心

但不应误判为：

- 直接引入后就拥有成熟联邦查询执行平台

## 3. 源码分析范围

本次重点分析以下代码与文档：

- `site/_docs/adapter.md`
- `site/_docs/model.md`
- `site/_docs/algebra.md`
- `core/src/main/java/org/apache/calcite/model/ModelHandler.java`
- `core/src/main/java/org/apache/calcite/schema/TranslatableTable.java`
- `core/src/main/java/org/apache/calcite/prepare/PlannerImpl.java`
- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcSchema.java`
- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcTable.java`
- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcConvention.java`
- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcRules.java`
- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcToEnumerableConverter.java`
- `core/src/test/java/org/apache/calcite/test/MultiJdbcSchemaJoinTest.java`

## 4. Calcite 联邦查询的总体机制

### 4.1 多数据源先统一成一个逻辑 Schema 世界

Calcite 并不是先设计“联邦执行器”，而是先设计：

- `Schema`
- `Table`
- `Model`
- `Adapter`

官方 model 文档明确，Calcite model 可以定义多个 schema，schema 类型包括：

- `map`
- `custom`
- `jdbc`

这意味着多个异构数据源可以先被挂到同一个逻辑命名空间中，再由 SQL 统一访问。

关键证据：

- `site/_docs/model.md`
- `core/src/main/java/org/apache/calcite/model/ModelHandler.java`

`ModelHandler` 的职责非常清晰：

- 读取 JSON/YAML model
- 遍历 root / schema / table / function
- 创建对应的 `SchemaPlus`
- 对 JDBC schema 走 `JdbcSchema.create(...)`

所以，Calcite 联邦查询的第一步不是“调度执行”，而是“统一挂载元数据入口”。

## 4.2 SQL 进入后先变成统一 RelNode 树

官方 algebra 文档明确：

- Every query is represented as a tree of relational operators.
- Planner rules transform expression trees.
- Cost model guides the process.

`PlannerImpl` 代码表明，Calcite 标准规划链路是：

1. `parse`
2. `validate`
3. `rel`
4. `transform`

并且底层使用 `VolcanoPlanner` 做规则驱动优化。

这意味着无论表来自 JDBC、Mongo、Elasticsearch 还是自定义 schema，只要接入 Calcite，都会先进入统一的关系代数世界。

关键代码：

- `core/src/main/java/org/apache/calcite/prepare/PlannerImpl.java`

## 4.3 每个数据源 adapter 通过 convention 声明“我能承接什么”

Calcite 不会直接把整个查询粗暴地下推给所有数据源，而是通过 convention 划分“在哪一层执行”。

对 JDBC 而言：

- 一个 JDBC 数据源对应一个 `JdbcConvention`
- `JdbcConvention.register(...)` 会注册一组 JDBC 专属规则

关键代码：

- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcConvention.java`

这说明 Calcite 的联邦优化不是“统一 SQL 然后一次性全量远端执行”，而是：

- 先统一逻辑计划
- 再看某个子树是否可以整体转成某个 adapter convention

## 4.4 不能继续下推时，回退到 Enumerable 本地执行

这是 Calcite 联邦查询最关键的真实实现点。

`JdbcToEnumerableConverter` 的代码直接表明：

- 它会根据 JDBC 子计划生成 SQL
- 调用 `ResultSetEnumerable.of(...)`
- 通过 JDBC 取回结果
- 在本地 `Enumerable` 世界中继续后续计算

因此，Calcite 的“联邦查询”很多时候实际是：

- 每个可下推子树去远端出数
- 本地做剩余 join / aggregate / sort / expression / merge

这不是缺陷，而是它当前架构的本质。

关键代码：

- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcToEnumerableConverter.java`

## 5. JDBC 适配器里联邦查询是怎么落地的

### 5.1 JdbcSchema：把一个 JDBC 数据源映射成一个 Schema

`JdbcSchema` 的类注释已经写得很直白：

- tables in the JDBC data source appear to be tables in this schema
- queries against this schema are executed against those tables
- pushing down as much as possible of the query logic to SQL

也就是说：

- `JdbcSchema` 不是查询引擎
- 它是 JDBC 数据源到 Calcite Schema 的桥

它负责：

- 持有 `DataSource`
- 识别 `catalog`、`schema`
- 生成 `SqlDialect`
- 为该数据源生成一个独立 `JdbcConvention`
- 暴露该 schema 下的表元数据

关键代码：

- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcSchema.java`

## 5.2 JdbcTable：把远端表转成 JdbcTableScan

`JdbcTable` 实现了 `TranslatableTable`。

其 `toRel(...)` 方法直接返回：

- `JdbcTableScan`

且绑定当前 `jdbcSchema.convention`。

这一步非常关键，意味着：

- 表一进入计划树，就已经带上“我属于哪个数据源 convention”
- 后续规则会围绕这个 convention 展开下推

关键代码：

- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcTable.java`

## 5.3 JdbcRules：定义 JDBC 能承接哪些关系算子

`JdbcRules` 中注册的规则包括：

- `JdbcJoinRule`
- `JdbcProjectRule`
- `JdbcFilterRule`
- `JdbcAggregateRule`
- `JdbcSortRule`
- `JdbcUnionRule`
- `JdbcIntersectRule`
- `JdbcMinusRule`
- `JdbcValuesRule`
- `JdbcToEnumerableConverterRule`

这意味着 JDBC adapter 在单数据源内，已经具备较完整的 SQL 子树承接能力。

可以直接理解为：

- Project pushdown：有
- Filter pushdown：有
- Aggregate pushdown：有
- Sort pushdown：有
- SetOp pushdown：有
- Join pushdown：有，但有限制

关键代码：

- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcRules.java`

## 5.4 JdbcJoinRule：join 下推并不是无条件成立

`JdbcJoinRule` 的几个限制很重要：

第一，`SEMI` / `ANTI` join 直接不转：

- 源码直接返回 `null`

第二，join condition 必须满足 `canJoinOnCondition(...)`：

- 仅支持有限类表达式
- 不支持任意复杂条件

第三，还要看目标方言 `dialect.supportsJoinType(joinType)`。

这说明 Calcite 虽然支持 JDBC join pushdown，但不是：

- 所有 join 都能推
- 所有表达式都能推
- 所有数据库都能统一推

## 6. 真正的“联邦查询”在 Calcite 里是怎么发生的

### 6.1 多个数据源统一挂载后，SQL 可以跨 schema 写

官方测试 `MultiJdbcSchemaJoinTest` 直接构造了两个独立数据库：

- `DB1`
- `DB2`

然后通过 Calcite root schema 同时挂载进去，再执行：

```sql
select table1.id, table1.field1
from db1.table1 join db2.table2 on table1.id = table2.id
```

这已经证明：

- Calcite 当前可以从语义层支持跨两个 JDBC schema 的单条 SQL 联邦查询

关键测试：

- `core/src/test/java/org/apache/calcite/test/MultiJdbcSchemaJoinTest.java`

## 6.2 但它不是跨 JDBC 数据源直接做远端互联

`JdbcConvention` 的类注释已经把这个问题讲透了：

- 如果有两个不同数据库，理论上可以从 `JDBC#A` 转到 `JDBC#B`
- 但“we don't do it currently”
- 若真要做，相当于要求数据库 B 去打开到数据库 A 的 database link

这是源码级明确信号：

- Calcite 当前没有做跨 JDBC convention 的直接互转执行
- 也没有内建 DB Link / FDW / foreign table 跨库重写机制

所以当前真实策略是：

- 单数据源内，尽量把子树压到该数据源 convention
- 跨源时，大概率退回本地 `Enumerable`

## 6.3 跨源 join 的真实执行方式通常是“远端出数，本地 join”

从 `JdbcToEnumerableConverter` 可见：

- JDBC 子树被转换成 SQL
- 远端执行
- 结果以 `Enumerable` 形式回到本地

一旦两个子树已经分别来自不同数据源，且没有跨 convention 直连能力，最自然的执行形态就是：

1. 左侧数据源执行其可下推子计划
2. 右侧数据源执行其可下推子计划
3. 两边结果回到本地 Enumerable
4. 由本地执行器做 join / filter / aggregate / sort

这就是 Calcite 联邦查询当前最核心的执行模型。

## 7. Calcite 当前联邦查询的主要优点

### 7.1 规划层能力强

Calcite 的优势始终在：

- SQL parser
- validator
- algebra
- rule planner
- cost-based transformation framework

这使它非常适合做联邦查询的规划内核。

## 7.2 多数据源抽象统一

通过：

- model
- schema SPI
- table SPI
- adapter

Calcite 已经把“多源统一建模”这件事做得比较干净。

## 7.3 单数据源内下推链完整

对 JDBC adapter 来说，至少在结构上：

- Scan
- Project
- Filter
- Join
- Aggregate
- Sort
- Union / Intersect / Minus

都已经具备规则和实现。

## 7.4 本地回退机制天然存在

Calcite 的架构允许：

- 能推的推
- 不能推的回退

这对于联邦查询平台来说是必要能力。

## 8. Calcite 当前联邦查询的不足

下面这些不足，不是“可能存在”，而是从源码结构和官方测试里已经能明确看到的。

### 8.1 缺少成品级联邦执行器

Calcite 最核心的短板是：

- 它是 planner framework
- 不是完整 query engine

它没有内建：

- coordinator / worker 分布式执行层
- 完整跨源算子调度层
- 统一 runtime resource 管理
- 查询隔离与查询治理

这意味着你拿 Calcite 做联邦查询，必须自己补：

- 子计划拆分
- 远端执行编排
- 并行执行调度
- 结果归并
- 错误恢复
- 限流与超时控制

## 8.2 跨不同 JDBC 数据源之间没有直接执行互转

`JdbcConvention` 已经明确写了：

- `JDBC#A -> JDBC#B` 现在不做

这会直接导致：

- 不能把跨库 join 自动重写成“在某个远端数据库里借助 DB Link 执行”
- 不能自动利用数据库原生跨库能力
- 跨源 join 更容易退回本地执行

这对高数据量场景是个硬限制。

## 8.3 Pushdown 不是统一能力模型，而是 adapter 局部实现

Calcite 现在的 pushdown 能力主要体现为：

- 每个 adapter 定义自己的 rel/operator/rule
- 每个 adapter 决定能下推哪些表达式

问题在于：

- 没有统一的“数据源能力矩阵”产品模型
- 没有现成的 connector capability negotiation 框架像 Flink/Trino 那么平台化
- 使用方必须自己管理：
  - 函数兼容
  - 类型兼容
  - join 能力
  - sort / limit / window / subquery 能力

## 8.4 Join Pushdown 能力受限

`JdbcJoinRule` 已经反映出几个现实限制：

- 半连接、反连接不能直接转 JDBC join
- join condition 仅支持有限表达式
- 还受数据库方言 join 支持能力约束

这说明：

- “join pushdown”存在
- 但不是产品级全覆盖 join pushdown

## 8.5 复杂跨源场景存在稳定性问题

`MultiJdbcSchemaJoinTest` 自己暴露了两个非常重要的问题：

- join 顺序反转时，可能出现 `CannotPlanException`
- 加 where 条件时，测试注释明确说“the result is wrong”

这不是推测，是官方测试里直接写出来的问题信号。

这说明在混合 convention、混合 source 类型场景下：

- 计划稳定性不够产品级
- 某些规则组合仍可能出错
- 需要使用方自己做额外约束和回避策略

## 8.6 缺少平台级元数据仓库与持久化能力

官方文档明确说：

- 当前 repository 不是 persisted
- DDL 操作是 in-memory repository

也就是说，Calcite 不会替你提供真正的平台仓库能力：

- 数据源注册表
- 服务定义仓库
- 发布版本
- 权限策略
- 审计流水
- 持久化 catalog repository

这些都需要你自己补。

## 8.7 缺少工业级服务化能力

官方文档自己也承认：

- 若要变成 industry-strength solution
- 还需要 packaging、repository persistence、authorization、security

所以 Calcite 当前是：

- 非常好的内核
- 不是现成服务产品

## 8.8 统计信息与代价模型需要使用方深度建设

虽然 Calcite 有代价模型框架，但对于联邦查询来说真正困难的是：

- 各数据源 row count 是否可信
- 过滤选择率是否可信
- 网络传输成本如何估算
- 远端执行与本地执行的成本如何统一比较
- 不同方言/索引/并发条件下代价如何动态调整

这些能力框架有，但产品级方案没有现成做完。

## 9. 对本项目的直接启示

结合源码，可以得出对本项目最重要的判断：

### 9.1 可以放心依赖 Calcite 的部分

- SQL 解析
- SQL 校验
- RelNode 统一表达
- 规则优化框架
- Join Reorder 基础能力
- Pushdown 候选识别
- 统一逻辑计划 / 物理计划生成基础

## 9.2 必须自己补的部分

- 数据源能力模型
- 统计信息模型
- 方言适配层
- 子计划拆分器
- 跨源执行编排器
- 本地结果整合器
- 计划缓存
- 发布校验链路
- 诊断与 explain 展示
- 元数据仓库与持久化
- 安全、鉴权、审计

## 9.3 必须主动规避的风险

- 不要假设跨源 join 都能远端下推
- 不要假设复杂表达式都能稳定保语义下推
- 不要假设 join 顺序无关紧要
- 不要把 Calcite 当现成联邦查询产品使用
- 对混合 source / 混合 convention 场景要建立白名单与回退策略

## 10. 最终判断

Calcite 1.41.0 当前联邦查询能力的正确定位是：

- 它已经具备联邦查询核心机制
- 但联邦查询的“执行平台能力”仍需要应用方自己构建

更准确地说，它现在做到的是：

- 多数据源统一建模
- 多数据源统一规划
- 单数据源内尽量下推
- 跨源时回退本地执行

它还没有做到的是：

- 工业级跨源执行平台
- 成品级能力协商系统
- 完整平台化仓库与服务治理
- 高稳定性的复杂跨源执行产品

所以，对本项目而言，Calcite 的最佳角色仍然是：

- 联邦 SQL 规划与优化核心

而不是：

- 直接作为完整联邦查询平台替代你们自己的执行编排层

## 11. 关键源码定位清单

### 11.1 官方文档

- `site/_docs/adapter.md`
- `site/_docs/model.md`
- `site/_docs/algebra.md`

### 11.2 规划入口

- `core/src/main/java/org/apache/calcite/prepare/PlannerImpl.java`

### 11.3 多数据源挂载

- `core/src/main/java/org/apache/calcite/model/ModelHandler.java`
- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcSchema.java`

### 11.4 表转 RelNode

- `core/src/main/java/org/apache/calcite/schema/TranslatableTable.java`
- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcTable.java`

### 11.5 JDBC convention 与规则

- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcConvention.java`
- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcRules.java`

### 11.6 回退到本地执行

- `core/src/main/java/org/apache/calcite/adapter/jdbc/JdbcToEnumerableConverter.java`

### 11.7 联邦查询测试样例

- `core/src/test/java/org/apache/calcite/test/MultiJdbcSchemaJoinTest.java`
