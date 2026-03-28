# Flink 跨数据库联邦查询调研报告

## 1. 文档目的

本文档整合此前三份 Flink 相关调研与源码分析结论，统一回答以下问题：

- Flink 是如何实现跨数据库、多数据源统一 SQL 查询的；
- Flink 的联邦查询能力在源码层的真实实现路径是什么；
- JDBC Connector 与 JDBC Catalog 的能力边界分别在哪里；
- Flink 适不适合作为数据服务系统第二阶段联邦 SQL 能力的基础方案；
- 对本项目可直接借鉴的设计点有哪些。

本文结论基于两类材料：

- Flink 2.2 主仓源码：`/Volumes/osdisk/java_code/01-open_source/flink-release-2.2`
- Flink JDBC Connector 源码：`/Volumes/osdisk/java_code/01-open_source/flink-connector-jdbc-main`

本文是当前项目内关于 Flink 联邦查询问题的唯一正式汇总文档。

## 2. 结论摘要

先给结论。

- Flink 可以做“跨数据库统一 SQL 查询”，但它不是一个专门面向异构数据库的联邦查询引擎。
- Flink 的真实实现模式是：
  - 用 `Catalog` 把外部库表注册进统一命名空间；
  - 用 `DynamicTableSource` / `LookupTableSource` 抽象不同数据源；
  - 用 `Planner` 基于 Calcite 与 Flink 自己的优化程序做解析、重写、下推与 Join Reorder；
  - 能下推的过滤、投影、limit、部分 lookup 条件由 connector 承接；
  - 不能下推的 join、聚合、补充过滤、结果整合，最终由 Flink Runtime 执行。
- 因此，Flink 更准确的定位不是“轻量联邦 SQL 执行内核”，而是“统一 SQL 计算平台”。
- Flink 对跨数据库查询最成熟的两条路径是：
  - 多个外部库表作为 batch source，被统一规划后在 Flink 内完成 join / aggregate；
  - 流式主表 + JDBC 维表，通过 lookup / temporal join 做实时关联。
- Flink 确实支持 Join Reorder、Dynamic Filtering、Runtime Filter 等优化，但：
  - Join Reorder 默认不开；
  - 下推能力是否生效，取决于 connector 是否实现对应 source ability；
  - JDBC connector 自身的下推边界明显，不能把复杂 SQL 整体回推数据库执行。
- 从 JDBC connector 源码看，当前可确认的 scan 级下推只有：
  - Projection Pushdown
  - Limit Pushdown
  - Filter Pushdown
- JDBC connector 不支持：
  - Aggregate Pushdown
  - Planner-level Partition Pushdown
  - Statistic Report
- JDBC Catalog 本质是“只读元数据接入层”，不是全功能 catalog，也不是联邦优化器。
- 对本项目而言，Flink 最值得借鉴的是：
  - `Catalog + Connector + Planner + Gateway` 的整体分层；
  - Source Ability 能力建模；
  - Join Reorder、动态过滤、Runtime Filter 等优化程序的组织方式。
- 但如果目标是建设“一个服务直接写一条联邦 SQL，面向异构数据库同步查询，并逐步演进为自助式 SQL 数据服务平台”，Flink 不适合作为最终联邦执行内核，Calcite 定制路线仍更契合。

## 3. Flink 联邦查询的总体定位

### 3.1 Flink 不把联邦查询作为独立产品能力提供

从源码结构与能力组织方式看，Flink 并没有单独做一层“Federation Engine”。它的跨数据库查询能力来自以下体系叠加：

- Table API / SQL
- Catalog
- Connector
- Planner / Optimizer
- Batch / Streaming Runtime
- SQL Gateway

因此，Flink 的跨数据库能力本质上是“统一 SQL 规划 + 分布式执行”，而不是“多数据库协同下推执行”。

### 3.2 Flink 的跨库查询本质

Flink 做跨数据库查询的基本路径是：

1. 把多个外部数据库对象注册为 Flink Table；
2. 在同一个 `TableEnvironment` / SQL Session 中执行统一 SQL；
3. Planner 生成统一逻辑计划与物理计划；
4. Connector 承接局部 pushdown；
5. 剩余计算在 Flink Runtime 内执行。

所以，Flink 的“联邦查询”本质是：

- 统一元数据接入
- 统一逻辑计划
- 统一优化框架
- 统一执行运行时

而不是：

- 对异构数据库进行最大化远端联邦下推

## 4. Flink 联邦查询的四层结构

从源码看，Flink 联邦查询由四层协同构成：

1. 入口层：`TableEnvironment` / `SQL Gateway`
2. 元数据层：`CatalogManager`
3. 数据接入层：`DynamicTableSource` / `LookupTableSource`
4. 规划执行层：`Planner` + Runtime ExecNode

对应核心源码位置：

- `flink-table-api-java/.../TableEnvironment.java`
- `flink-table-api-java/.../internal/TableEnvironmentImpl.java`
- `flink-table-common/.../connector/source/DynamicTableSource.java`
- `flink-table-common/.../connector/source/LookupTableSource.java`
- `flink-table-common/.../connector/source/abilities/`
- `flink-table-planner/...`
- `flink-sql-gateway/...`

这四层分工清晰：

- 入口层负责接收 SQL；
- 元数据层负责解析表对象；
- 接入层负责暴露外部系统读能力；
- 规划执行层负责下推决策、全局优化与最终执行。

## 5. 统一 SQL 入口：`TableEnvironmentImpl`

### 5.1 入口不是数据库 server，而是 `TableEnvironment`

`TableEnvironment.java` 明确把自己定义为：

- Table / SQL 程序入口；
- 连接外部系统；
- 注册 / 获取 catalog 中的表；
- 执行 SQL。

`TableEnvironment.create(...)` 最终进入 `TableEnvironmentImpl.create(...)`。

### 5.2 初始化链路

`TableEnvironmentImpl.create(EnvironmentSettings settings)` 会初始化：

- `Executor`
- `CatalogStore`
- `CatalogManager`
- `FunctionCatalog`
- `Planner`

这说明 Flink 从一开始就把“元数据管理”和“SQL 规划执行”作为统一体系装配。

### 5.3 SQL 执行链路

`TableEnvironmentImpl.executeSql(String statement)` 的核心流程是：

1. `getParser().parse(statement)` 把 SQL 解析成 `Operation`
2. `executeInternal(operation)` 执行

`sqlQuery(...)`、`explainSql(...)`、`compilePlanSql(...)` 也是同一条链路的变体。

所以 Flink 的跨源 SQL 首先是“统一 SQL -> 统一计划”的问题，而不是“把 SQL 直接发给某个数据库”。

## 6. 元数据层：`CatalogManager` 与 JDBC Catalog

### 6.1 `CatalogManager` 负责统一命名空间

`TableEnvironmentImpl` 持有 `CatalogManager`，所有对象解析、路径限定、`useCatalog(...)`、`listTables(...)` 等都通过这一层。

这意味着跨库查询的前提是：

- 多个外部库表先进入 Flink catalog 命名空间；
- SQL 再通过 `catalog.database.table` 或当前 catalog/database 进行对象解析。

因此，Flink 的“跨库”第一步是“统一元数据解析”。

### 6.2 `Catalog` 只解决“怎么让 Planner 看见表”

从职责上看，`CatalogManager` 与各类 `Catalog` 负责：

- 对象注册
- 路径解析
- 元数据获取

但不负责：

- 联邦优化
- 全局执行编排

所以 `Catalog` 不是联邦执行器，它只是让 Flink 能理解外部对象。

### 6.3 JDBC Catalog 的真实定位

JDBC Catalog 的核心基类是：

- `flink-connector-jdbc-core/.../AbstractJdbcCatalog.java`

它在源码上的能力边界非常清楚。

#### 6.3.1 支持的读能力

支持：

- `open`
- `databaseExists`
- `getDatabase`
- `getTable`
- `listDatabases`
- `listTables`
- `tableExists`
- `getFactory`

其中 `getTable(...)` 还会：

- 读取 JDBC metadata 获取主键；
- 执行 `SELECT * FROM table` 获取 `ResultSetMetaData`；
- 映射成 Flink `Schema`；
- 再返回 `CatalogTable`。

#### 6.3.2 不支持的写能力

大量方法直接：

- `throw new UnsupportedOperationException()`

包括：

- `createDatabase`
- `dropDatabase`
- `alterDatabase`
- `createTable`
- `alterTable`
- `dropTable`
- `renameTable`
- function 相关
- partition 相关
- statistics alter 相关

#### 6.3.3 统计信息基本不可用

以下接口直接返回：

- `CatalogTableStatistics.UNKNOWN`
- `CatalogColumnStatistics.UNKNOWN`

这意味着 JDBC Catalog 是只读元数据接入层，而不是全功能 catalog，也无法为代价优化器提供高质量统计数据。

### 6.4 当前源码里明确存在的 JDBC Catalog

当前 `flink-connector-jdbc-main` 源码中，明确有 catalog 实现的数据库包括：

- `MySqlCatalog`
- `PostgresCatalog`
- `OceanBaseCatalog`

各自特点如下。

#### 6.4.1 MySQL Catalog

- 通过 `information_schema.schemata` 列数据库；
- 通过 `information_schema.tables` 列表；
- 过滤内建库：
  - `information_schema`
  - `mysql`
  - `performance_schema`
  - `sys`

#### 6.4.2 Postgres Catalog

- 通过 `pg_database` 列数据库；
- 通过 `information_schema.schemata` 列 schema；
- 通过 `information_schema.tables` 列表；
- 过滤内建 database 与 schema；
- 表名在 Flink 侧可表示为 `schema.table`，再由 `PostgresTablePath` 解析。

#### 6.4.3 OceanBase Catalog

- 支持 `compatible-mode`
- 兼容 MySQL 模式与 Oracle 模式
- `listDatabases` / `listTables` / `tableExists` 的 SQL 会随兼容模式切换

### 6.5 JDBC Catalog 的装配方式

`JdbcCatalogFactory` 最终调用：

- `JdbcFactoryLoader.loadCatalog(...)`

`JdbcFactoryLoader` 再通过 `ServiceLoader` 找到能 `acceptsURL(url)` 的唯一 `JdbcFactory`。

这说明：

- JDBC Catalog / Dialect 的选择是按 URL 自动匹配；
- 它是插件发现机制，不是动态联邦能力协商机制。

## 7. 数据接入抽象：`DynamicTableSource` 与 `LookupTableSource`

### 7.1 `DynamicTableSource` 是统一 source 抽象

`DynamicTableSource.java` 定义了统一的动态表 source 抽象：

- 可以作为 `ScanTableSource` 读取；
- 也可以作为 `LookupTableSource` 按 key 查询；
- Planner 会根据查询类型决定使用哪条路径；
- Planner 会根据 source 声明的 ability 对 source 实例进行“变异”。

这意味着 Flink 并不是为 JDBC、Kafka、Hive、Iceberg 各自做一套联邦查询框架，而是统一抽象为 source，再在上层做统一优化。

### 7.2 `LookupTableSource` 用于维表 / Temporal Join

`LookupTableSource.java` 明确：

- Planner 会从查询中推导 lookup key；
- 通过 `LookupContext#getKeys()` 交给 source；
- 当前语义主要是 insert-only lookup。

这也是 Flink 更成熟的一条跨源能力路径：

- 主表在 Flink 内流动；
- 维表在外部数据库；
- 运行时做按 key lookup。

## 8. 下推机制：Flink 用 Source Ability 建模

Flink 的 pushdown 核心不是写死在某种数据库实现里，而是定义在 source ability 接口中：

- `SupportsFilterPushDown`
- `SupportsProjectionPushDown`
- `SupportsLimitPushDown`
- `SupportsPartitionPushDown`
- `SupportsAggregatePushDown`
- `SupportsStatisticReport`

这套设计是 Flink 联邦查询能力的关键，也是本项目最值得借鉴的设计点之一。

### 8.1 Filter Pushdown

`SupportsFilterPushDown` 的语义是：

- Planner 把过滤条件拆成 conjunctive form；
- Source 返回：
  - `acceptedFilters`
  - `remainingFilters`

这意味着 filter pushdown 可以是部分成功：

- 一部分在 source 端处理；
- 一部分保留给上层 runtime。

### 8.2 Projection Pushdown

`SupportsProjectionPushDown` 允许 source 接受：

- 顶层列裁剪；
- 可选 nested projection；
- 投影后的最终 `producedDataType`

### 8.3 Limit Pushdown

`SupportsLimitPushDown` 是 best-effort 语义：

- source 可以提前裁剪数据；
- Flink 仍保留上层 limit 节点做语义兜底。

### 8.4 Partition Pushdown

`SupportsPartitionPushDown` 允许：

- 列出分区；
- 传入剩余分区列表；
- 从而减少实际扫描范围。

### 8.5 Aggregate Pushdown

`SupportsAggregatePushDown` 支持把 local aggregate 压进 source，但限制严格：

- 复杂 grouping sets 不支持；
- 聚合中的复杂表达式不支持；
- ordering / filter 聚合不支持；
- 策略是 all-or-nothing。

### 8.6 Statistic Report

`SupportsStatisticReport` 允许 source 返回估算统计信息，这会影响：

- Join Reorder
- Broadcast Join
- Runtime Filter 注入判断

## 9. Planner 如何真正消费这些能力

光有 ability 接口不够，关键在 Planner 是否真的会用。

答案是：会，而且是通过明确的 rule 与 optimize program。

### 9.1 Filter 下推：`PushFilterIntoTableSourceScanRule`

匹配模式：

- `Filter`
- 下接 `LogicalTableScan`
- table 是 `TableSourceTable`
- source 实现了 `SupportsFilterPushDown`

执行逻辑：

1. 抽取可转换 predicate 与不可转换 predicate；
2. 调用 source 的 `applyFilters(...)`；
3. 生成新的 `TableSourceTable`；
4. 如果还有剩余 filter，则保留上层 `Filter`。

这说明 Flink 的 filter pushdown 是规则驱动、可部分下推、可自动补偿的。

### 9.2 Projection 下推：`PushProjectIntoTableSourceScanRule`

匹配：

- `LogicalProject`
- 下接 `LogicalTableScan`

执行逻辑包括：

- 判断 source 是否支持 projection pushdown；
- 计算所需物理列、主键列、metadata 列；
- 生成 `ProjectPushDownSpec`；
- 应用到新的 source 副本；
- 重写上层 `Project`。

说明 Flink 的 projection pushdown 不是简单列裁剪，还会兼顾主键与 metadata 语义。

### 9.3 Limit 下推：`PushLimitIntoTableSourceScanRule`

这个规则只在“无 `ORDER BY`、只有 `LIMIT`”时触发。

源码中明确写明：

- limit pushdown 只是 best-effort；
- 原有 limit 不会移除；
- 因为 source 很难精确保证全局 limit 语义。

### 9.4 Partition 下推：`PushPartitionIntoTableSourceScanRule`

该规则会：

1. 判断 source 是否实现 `SupportsPartitionPushDown`；
2. 判断表是否分区表；
3. 从 filter 中抽取 partition predicate；
4. 调用 `PartitionPruner` 做裁剪；
5. 若剩余分区为空则改写成 empty；
6. 否则生成 `PartitionPushDownSpec` 并应用到新 source。

### 9.5 Aggregate 下推：`PushLocalAggIntoScanRuleBase`

聚合下推不是简单逻辑规则，而是物理阶段规则：

- `PushLocalAggIntoScanRuleBase`
- `AggregatePushDownSpec`

它会把 local aggregate 尝试合并进 scan，并改写 scan 输出 row type。

### 9.6 这些规则是否真的会执行

`FlinkBatchProgram.scala` 里，优化程序会显式串起：

- `PUSH_PARTITION_DOWN_RULES`
- `PUSH_FILTER_DOWN_RULES`
- `FlinkRecomputeStatisticsProgram`
- `JOIN_REORDER`

而且顺序上先做分区裁剪，再做 filter 下推，再重算统计信息。

这说明 Flink 的优化不是零散规则堆积，而是按阶段组织的优化程序链。

## 10. Join Reorder、Dynamic Filtering 与 Runtime Filter

### 10.1 Join Reorder

`OptimizerConfigOptions` 中定义：

- `table.optimizer.join-reorder-enabled`
- 默认值 `false`

只有显式开启后，`FlinkBatchProgram.scala` 才会装配：

- `JOIN_REORDER_PREPARE_RULES`
- `JOIN_REORDER_RULES`

核心规则 `FlinkJoinReorderRule` 表明：

- 它在 `MultiJoin` 上触发；
- 小规模 join 图走 `FlinkBushyJoinReorderRule`；
- 超过阈值走 Calcite 的 `LoptOptimizeJoinRule`；
- 阈值由 `table.optimizer.bushy-join-reorder-threshold` 控制，默认 `12`。

因此，Flink 的 Join Reorder 本质上是：

- 直接建立在 Calcite 多表 join 重排能力之上；
- 再加上 Flink 自己的 bushy 策略控制。

### 10.2 Dynamic Partition Pruning

`FlinkDynamicPartitionPruningProgram` 会把：

- 普通 `BatchPhysicalTableSourceScan`

改写为：

- `BatchPhysicalDynamicFilteringTableSourceScan`

并在 fact/dim join 模式下引入动态过滤数据采集链路。

这说明 Flink 不只是做静态 pushdown，还会在 runtime 基于 join build side 数据缩小 fact 扫描范围。

### 10.3 Runtime Filter

`FlinkRuntimeFilterProgram.java` 会在满足条件的 batch join 上注入：

- `BatchPhysicalLocalRuntimeFilterBuilder`
- `BatchPhysicalGlobalRuntimeFilterBuilder`
- `BatchPhysicalRuntimeFilter`

适用条件包括：

- join 类型合适；
- 是 `BatchPhysicalHashJoin` 或 `BatchPhysicalSortMergeJoin`；
- build / probe 数据量满足阈值；
- 过滤收益足够。

这已经是典型现代查询引擎式的 join 优化。

但要注意：

- 这些优化发生在 Flink 自己的执行图里；
- 不是远端数据库之间的联邦协同执行。

## 11. 两条主要跨库实现路径

### 11.1 路径一：多个外部表作为 batch source

典型方式：

1. 用 catalog 或 `CREATE TABLE` 接入多个数据库表；
2. 在同一个 SQL 会话中执行 join / filter / aggregate；
3. 各自做局部 pushdown；
4. 数据回到 Flink Runtime 内完成全局计算。

这条路径最接近“跨数据库联邦 SQL”，但执行主体仍是 Flink。

### 11.2 路径二：流式主表 + JDBC 维表 lookup / temporal join

典型方式：

- 主流来自 Kafka / CDC / 事件流；
- 维表来自 MySQL / PostgreSQL / Oracle 等数据库；
- Flink 对每条主表记录发起 lookup 查询；
- 维表侧附加条件也可以一并带进 lookup SQL。

这是 Flink 当前最成熟的一类跨源能力，但本质属于 lookup enrichment，不是通用联邦 join 执行。

## 12. JDBC Connector 的源码级能力边界

JDBC Connector 的核心类是：

- `flink-connector-jdbc-core/.../JdbcDynamicTableSource.java`

它的类定义已经直接说明能力边界：

```java
public class JdbcDynamicTableSource
        implements ScanTableSource,
                LookupTableSource,
                SupportsProjectionPushDown,
                SupportsLimitPushDown,
                SupportsFilterPushDown
```

也就是说，JDBC source 同时支持：

- scan source
- lookup source
- projection pushdown
- limit pushdown
- filter pushdown

但不支持更多 source ability。

### 12.1 JDBC scan 的远端 SQL 形态

`JdbcDynamicTableSource.getScanRuntimeProvider(...)` 的处理流程是：

1. 构建基础 `SELECT`
2. 若配置了 range split，则加上 `BETWEEN ? AND ?`
3. 拼接 `resolvedPredicates`
4. 若设置了 `limit`，则加上 `dialect.getLimitClause(limit)`

实际远端 SQL 形态大致是：

```sql
SELECT <projected_columns>
FROM <table>
WHERE (<partition range predicate>) AND (<pushed filters>)
LIMIT ...
```

### 12.2 Projection Pushdown

`applyProjection(...)` 会直接更新 `physicalRowDataType`，最终体现在远端 SQL 的 `SELECT` 列表中。

同时：

- `supportsNestedProjection()` 返回 `false`

所以 JDBC projection pushdown 的边界是：

- 支持顶层列裁剪
- 不支持 nested projection

### 12.3 Limit Pushdown

`applyLimit(long limit)` 只是记录 limit，再在 scan SQL 拼接方言 limit 子句。

所以 JDBC connector 确实支持远端 limit，但仍由 Flink 上层 limit 做语义兜底。

### 12.4 Filter Pushdown

`JdbcDynamicTableSource.applyFilters(...)` 会根据 `filter.handling.policy` 分两种：

- `NEVER`：完全不推
- `ALWAYS`：尽量推可解析 filter

真正负责表达式翻译的是：

- `JdbcFilterPushdownPreparedStatementVisitor`

源码上只支持以下表达式：

- `=`
- `<`
- `<=`
- `>`
- `>=`
- `<>`
- `AND`
- `OR`
- `LIKE`
- `IS NULL`
- `IS NOT NULL`

并只支持有限字面量类型：

- `CHAR`
- `VARCHAR`
- `BOOLEAN`
- `DECIMAL`
- `TINYINT`
- `SMALLINT`
- `INTEGER`
- `BIGINT`
- `FLOAT`
- `DOUBLE`
- `DATE`
- `TIME_WITHOUT_TIME_ZONE`
- `TIMESTAMP_WITHOUT_TIME_ZONE`

因此，JDBC filter pushdown 在源码上是“有限表达式子集”，不是通用表达式下推器。

### 12.5 为什么测试里的 `IN (...)` 能工作

源码测试里可以看到 `IN (...)` 场景，但 visitor 并没有单独处理 `IN`。

更合理的解释是：

- Flink Planner 在更前面的表达式归一化阶段，已经把 `IN` 改写成了 `=` + `OR`；
- JDBC visitor 最终只处理自己能识别的布尔组合表达式。

因此不能把“测试里 `IN` 能通过”理解为 JDBC visitor 原生支持 `IN` 语义节点。

### 12.6 JDBC 没有实现的能力

从源码可直接确认 JDBC connector 没有实现：

- `SupportsPartitionPushDown`
- `SupportsAggregatePushDown`
- `SupportsStatisticReport`

因此不能期待它支持：

- Planner-level partition pruning
- aggregate pushdown
- source statistics report

### 12.7 分区扫描不等于 `SupportsPartitionPushDown`

JDBC connector 虽然支持：

- `scan.partition.column`
- `scan.partition.lower-bound`
- `scan.partition.upper-bound`
- `scan.partition.num`

但这是 connector 自己的 range split 读取配置，不等于实现了 Flink 的 `SupportsPartitionPushDown`。

两者需要严格区分：

- 前者是 connector 内部分片扫描；
- 后者是 Planner 可识别的 partition pruning 能力。

## 13. JDBC Lookup Join 的真实边界

`JdbcRowDataLookupFunction` 会按 `keyNames` 生成 lookup SQL：

```java
options.getDialect().getSelectFromStatement(options.getTableName(), fieldNames, keyNames)
```

随后在 `establishConnectionAndStatement()` 中再拼接附加谓词：

- key 条件
- `resolvedPredicates`

所以 lookup join 的远端 SQL 形态是：

```sql
SELECT ...
FROM dim_table
WHERE key = ?
  AND (<extra pushed predicates>)
```

这说明：

- JDBC lookup join 不只是“按 key 查”；
- 维表侧可下推的附加 filter 会一起进入远端 SQL。

但本质上仍然是：

- 左表数据在 Flink 中流动；
- 每条记录触发 lookup；
- 不是把整个 join 交给数据库执行。

## 14. JDBC Dialect 的职责边界

`JdbcDialect` 主要负责：

- `getRowConverter`
- `getLimitClause`
- `quoteIdentifier`
- `getSelectFromStatement`
- `getInsert/Update/Delete/Upsert...`
- 类型校验

这说明 dialect 的定位是：

- SQL 方言适配器
- 标识符与类型适配器

而不是：

- source capability 决策器
- 联邦优化决策器

换句话说，JDBC connector “能否下推某项能力”主要由 `JdbcDynamicTableSource` 是否实现对应 ability 决定，dialect 只负责在已经确定要推时生成相应 SQL 片段。

### 14.1 当前已看到的方言差异

源码可见的差异主要体现在：

- MySQL: `LIMIT n`，反引号引用标识符
- PostgreSQL: `LIMIT n`，标识符基本原样
- Oracle: `FETCH FIRST n ROWS ONLY`
- DB2: `FETCH FIRST n ROWS ONLY`

再加上：

- 类型精度范围
- upsert 语法

但并没有看到按数据库类型扩展 source ability 的机制，例如：

- MySQL 支持 aggregate pushdown
- PostgreSQL 支持 statistics pushdown
- Oracle 支持 partition pushdown

源码里没有这类 capability matrix。

## 15. SQL Gateway 的真实角色

`SqlGatewayServiceImpl` 的职责主要是：

- session 管理
- operation 提交
- statement 执行调度

而 `OperationExecutor.getTableEnvironment(...)` 最终仍然会：

- 创建 `EnvironmentSettings`
- 创建 `Executor`
- 创建 `Planner`
- 返回 `TableEnvironment`

所以 SQL Gateway 的本质是：

- 把 Flink SQL 服务化

而不是：

- 提供独立于 Table Planner 的联邦执行器

不能把 SQL Gateway 理解成一个独立联邦查询产品。

## 16. Flink 对联邦查询的真实能力边界

综合主仓源码与 JDBC connector 源码，可以给出更准确的能力边界。

### 16.1 Flink 具备的能力

- 统一多数据源元数据接入
- 统一 SQL 解析与逻辑计划
- 基于 source ability 的局部下推
- Join Reorder
- Dynamic Filtering
- Runtime Filter
- 流式 lookup / temporal join

### 16.2 Flink 不具备或不擅长的能力

- 把复杂跨源 SQL 整体联邦地下推回多个数据库执行
- 依赖 JDBC source 做 aggregate pushdown
- 依赖 JDBC source 做 planner-level partition pruning
- 依赖 JDBC Catalog/source 提供高质量统计信息
- 面向异构数据库同步查询场景的低延迟专用联邦执行

### 16.3 普通跨源 join 最终在哪里执行

普通跨源 join 的最终执行节点通常是：

- `BatchPhysicalHashJoin`
- `BatchPhysicalSortMergeJoin`
- `BatchExecHashJoin`
- `BatchExecSortMergeJoin`

因此，全局 join 计划最终仍由 Flink Runtime 执行，而不是由远端数据库协同执行。

## 17. 对本项目的直接启示

### 17.1 最值得借鉴的是分层

Flink 最值得借鉴的不是把 Runtime 原样搬过来，而是以下分层：

1. 元数据层：统一 catalog / schema / function
2. 连接器层：能力显式建模
3. Planner 层：统一 SQL、逻辑优化、代价优化、下推决策
4. 执行层：自研联邦执行器或接入下游执行体系

### 17.2 Source Ability 模型很适合本项目第二阶段

本项目如果要支持“一条联邦 SQL”，建议前置建模：

- Filter Pushdown
- Project Pushdown
- Limit Pushdown
- Aggregate Pushdown
- Sort Pushdown
- Join Pushdown
- Statistics Report

然后由 Planner 基于 connector capability 做计划改写。

### 17.3 Join Reorder 可直接借鉴 Calcite 路线

Flink 已经证明：

- Join Reorder 可以建立在 Calcite `MultiJoin + LoptOptimizeJoinRule` 上；
- 小规模 join 图再叠加 bushy 策略；
- 配合统计信息更有效。

这对本项目第二阶段实现 Join Reorder 很有价值。

### 17.4 JDBC Connector 更适合作为“接入层”

JDBC connector 很适合承担：

- 远端表接入
- 基础 filter/project/limit 下推
- lookup 维表访问

但它不适合直接充当：

- 完整联邦执行引擎
- 复杂跨源 SQL 的远端下推器

否则最终会退化成：

- 各源只做少量局部过滤与列裁剪；
- 大量数据回流执行层；
- join / aggregate 压力全部落在平台自身。

## 18. 最终判断

从 Flink 2.2 主仓源码和 JDBC Connector 源码看，Flink 的“联邦查询”能力成立，但其本质应准确表述为：

- 基于统一 Catalog / Connector 抽象的多源 SQL 规划与分布式执行能力

而不是：

- 面向异构数据库同步查询服务的专用联邦 SQL 引擎

更直白地说：

- Flink 能跨源查
- 也能做不少优化
- 但主要还是在自己的 Runtime 里算
- JDBC connector 也只能承接有限的局部下推

对本项目而言，Flink 最有价值的是“架构思想、能力分层与优化组织方式”，不是把它直接当成第二阶段联邦 SQL 服务内核。
