# Flink、Calcite、Trino 联邦查询选型对比报告

## 1. 文档目的

本文档基于官方资料，对 Apache Flink、Apache Calcite、Trino 三种技术在跨数据库联邦查询场景下的定位、实现方式、优化能力、平台化能力、工程复杂度与适用边界进行系统对比，为数据服务系统第二阶段技术路线提供决策依据。

本文聚焦本项目场景，不做泛化选型。评估前提如下：

- 目标系统是“数据服务平台”，不是通用流批计算平台
- 希望一个服务直接维护一条联邦 SQL
- 希望自动做 Join Reorder、Filter Pushdown、Project Pushdown
- 希望支持更复杂表达式和 SQL 方言
- 希望逐步演进为“自助式 SQL 数据服务平台”
- 偏向瀑布式、稳扎稳打的实施方式

## 2. 先给结论

结论分三层：

### 2.1 如果目标是“嵌入式联邦 SQL 内核”，首选 Calcite

原因：

- Calcite 天然就是 SQL Parser / Validator / Relational Algebra Planner / Optimizer 内核
- 适合嵌入 Spring 服务中，做统一 SQL 解析、语义校验、逻辑计划、规则优化
- 不强绑定自己的重运行时
- 最适合本项目当前“平台自研执行编排层”的路线

### 2.2 如果目标是“独立联邦查询平台/查询引擎”，Trino 更像直接可用产品

原因：

- Trino 本质就是面向多数据源查询的分布式 SQL Query Engine
- Connector、Pushdown、Cost-based Optimization、EXPLAIN、Catalog 体系完整
- 更接近“查询平台”而不是“SQL 规划库”

但问题也明确：

- 需要引入独立分布式查询集群
- 与当前 Spring 服务形态耦合较弱
- 平台化能力虽强，但“服务配置、版本发布、API 暴露”仍需外围系统二次建设

### 2.3 如果目标是“流批一体、实时关联、维表补充、SQL Gateway 平台”，Flink 更合适

原因：

- Flink 擅长流批一体、动态表、Temporal Join、实时数仓、SQL Gateway
- 也能跨数据库查询

但它不适合当前项目作为首选联邦查询执行层，原因在于：

- Flink 的核心是计算平台，不是轻量联邦查询微服务内核
- 很多跨库 Join 实际是在 Flink Runtime 中完成
- JDBC Source / Lookup 的在线查询语义和 API 查询服务并不完全匹配

### 2.4 对本项目的推荐结论

当前阶段推荐继续采用：

- `Apache Calcite` 作为联邦 SQL 规划与优化核心
- 平台自研数据源能力模型、方言适配、子计划拆分、结果整合、缓存、诊断与发布链路

中期备选路线：

- 若未来演进为“统一分析查询平台”，可评估引入 Trino
- 若未来重点转向“流批一体实时数据处理平台”，可评估引入 Flink

## 3. 对比维度说明

本报告按以下维度对比：

- 产品定位
- 联邦查询实现方式
- SQL 解析与校验
- Join Reorder
- Filter / Projection / Aggregation / Limit Pushdown
- SQL 方言兼容
- 执行模型
- 平台化能力
- 可嵌入性
- 运行时复杂度
- 对本项目适配性

## 4. 三者本质定位

### 4.1 Apache Calcite

Calcite 官方资料明确，它是用于：

- SQL parsing
- query optimization
- data virtualization / federation
- materialized view rewrite

并提供：

- Adapter
- Planner
- Relational Algebra
- JDBC driver（通过 Avatica）

Calcite 的本质不是最终用户直接使用的完整查询平台，而是“联邦 SQL 内核”。

它最核心的价值是：

- 统一 SQL 表示
- 统一关系代数表示
- 可扩展优化规则
- 可扩展代价模型
- 可扩展适配器

### 4.2 Trino

Trino 官方文档把自己定义为分布式 SQL 查询引擎，其核心特征是：

- 面向多数据源查询
- 通过 Connector 访问外部数据源
- 具备 cost-based optimization
- 具备丰富 pushdown
- 具备完整 coordinator / worker 执行架构

Trino 的本质是“现成可用的联邦查询平台”。

### 4.3 Apache Flink

Flink 官方文档强调：

- Flink SQL enables streaming and batch applications using standard SQL
- Flink SQL is based on Apache Calcite
- SQL Gateway 提供远程 SQL 提交能力

Flink 的本质是“流批一体分布式计算平台”，其跨数据库查询能力是：

- Catalog
- Connector
- Planner
- Runtime

共同作用的结果，而不是专门面向在线联邦查询服务的轻量引擎。

## 5. 联邦查询实现模型对比

### 5.1 Calcite：规划内核 + 你自己的执行层

典型实现路径：

1. 接入多数据源 Schema / Adapter
2. 解析 SQL
3. 校验 SQL
4. 转 RelNode
5. 应用规则优化
6. 输出逻辑计划 / 物理计划
7. 由你自己的平台负责：
   - 子计划拆分
   - 远端 SQL 生成
   - JDBC 执行
   - 结果整合
   - 缓存
   - 发布与诊断

这意味着：

- 你拿到的是“最可控的规划核心”
- 但执行层、数据源能力模型、方言适配都需要自己建设

### 5.2 Trino：统一 SQL + 统一执行平台

典型实现路径：

1. 为每个外部数据源配置 Catalog / Connector
2. 用户直接写跨源 SQL
3. Trino Parser / Analyzer / Optimizer 生成计划
4. Connector 汇报可下推能力
5. Trino 在集群内执行剩余算子
6. 返回结果

这意味着：

- Trino 天然就是联邦查询执行平台
- 你不必自己写执行器
- 但你也不再拥有“嵌入式内核级控制权”

### 5.3 Flink：统一 SQL + 计算平台运行时

典型实现路径：

1. 通过 Catalog / Connector 把多个数据库对象映射成 Flink Table
2. 统一 SQL 规划
3. 由 Source Ability 决定下推
4. 剩余 Join / Aggregate / Shuffle 在 Flink Runtime 中执行
5. 通过 SQL Gateway / TableEnvironment 获取结果

这意味着：

- Flink 也能做联邦查询
- 但重点是“把多源数据放进统一计算平台里算”
- 不等于“为在线数据服务打造的轻量联邦引擎”

## 6. SQL 解析、校验、关系代数能力

### 6.1 Calcite

这是 Calcite 的绝对强项。

官方 `Planner` API 明确提供完整流程：

- `parse`
- `validate`
- `rel`
- `transform`

并且 `Algebra` 文档明确：

- 每个查询都表示为关系运算树
- 规则可在保持语义不变的前提下转换表达式树
- 可扩展 operators、rules、cost model、statistics

适配本项目时，Calcite 非常适合做：

- 联邦 SQL 语法校验
- 语义校验
- 参数类型校验
- 逻辑计划生成
- 下推候选识别
- 优化前后计划对比

### 6.2 Trino

Trino 当然也有成熟的 SQL 解析、分析和规划能力，但这些能力是“平台内部能力”，不是像 Calcite 那样面向你开放的轻量可嵌入规划框架。

换句话说：

- 你可以使用 Trino
- 但你不是在“嵌入 Trino Planner SDK 构建自己的服务”
- 你更多是在“接入一个现成分布式查询引擎”

### 6.3 Flink

Flink SQL 官方明确说明：

- Flink SQL 基于 Apache Calcite

所以在 SQL 解析、关系代数规划思想上，Flink 本质上站在 Calcite 之上。

但对本项目来说，关键区别不是“能不能 parse/validate”，而是：

- Flink 这些能力和其计算运行时深度绑定
- 不是单纯为了嵌入式联邦查询服务而设计

## 7. Join Reorder 能力对比

### 7.1 Calcite

Calcite 能做 Join Reorder，但前提是：

- 你配置对应规则
- 你提供必要统计信息或代价模型
- 你决定规则集合和优化阶段

也就是说：

- Calcite 给你能力
- 但默认不替你构建完整产品级策略

优点是可控。

缺点是需要自己建设。

### 7.2 Trino

Trino 官方成本优化文档明确：

- `join_reordering_strategy` 默认是 `AUTOMATIC`
- 支持 full automatic join enumeration
- 使用 Connector 提供的表统计信息估算成本
- 若无法计算成本，会退回 `ELIMINATE_CROSS_JOINS`

这说明：

- Trino 的 Join Reorder 是产品级成熟能力
- 默认就是自动模式
- 对联邦查询平台很友好

### 7.3 Flink

Flink 官方配置文档明确：

- `table.optimizer.join-reorder-enabled` 默认 `false`

这与 Trino 差异明显：

- Flink 有 Join Reorder 能力
- 但默认并不激进启用
- 更像“计算平台里的一个可选优化开关”

### 7.4 小结

按“开箱即用程度”排序：

1. Trino
2. Calcite（可做，但需自己构建）
3. Flink（能做，但默认不强调这一点）

按“可控性”排序：

1. Calcite
2. Trino
3. Flink

## 8. Pushdown 能力对比

### 8.1 Calcite

Calcite 不是现成执行引擎，因此“pushdown”在 Calcite 里本质上是：

- 通过规则识别哪些逻辑可被远端数据源承接
- 把逻辑计划转换成更适合下推的形式
- 最终由你的 Adapter / 执行层决定怎么落地

优点：

- 理论上最灵活
- 可为每种数据库做细粒度规则

缺点：

- 没有现成产品级 pushdown 套件
- 需要你自己把“优化决策”变成“执行下推”

### 8.2 Trino

Trino 官方 Pushdown 文档明确支持展示和说明：

- Predicate pushdown
- Projection pushdown
- Dereference pushdown
- Aggregation pushdown
- Join pushdown
- Limit pushdown
- Top-N pushdown

同时官方 Connector 开发文档直接把这些能力作为 ConnectorMetadata 的扩展点。

这说明：

- Trino 的 pushdown 体系非常完整
- 它在“联邦查询产品化”上明显成熟

但仍需注意：

- Pushdown 是否生效取决于具体 Connector 与底层数据库
- 不是所有 Connector 都支持全部 pushdown

### 8.3 Flink

Flink 的 pushdown 来自 Source Ability 模型。

官方 FLIP-95 明确提出：

- `SupportsFilterPushDown`
- `SupportsProjectionPushDown`
- `SupportsLimitPushDown`
- `SupportsPartitionPushDown`

说明 Flink 具备标准化 pushdown 框架。

但 Flink 官方 JDBC 文档并没有像 Trino 那样，把各种 pushdown 直接作为产品级能力清单对外宣示。实际可用性更多依赖：

- Connector 版本
- 数据源方言
- 具体表达式
- Planner 与 Connector 的协商结果

### 8.4 小结

按“联邦查询产品视角下的 pushdown 完整度”排序：

1. Trino
2. Calcite（理论灵活，但需平台自己补全）
3. Flink

按“最适合自定义下推规则能力建模”排序：

1. Calcite
2. Trino
3. Flink

## 9. SQL 方言兼容与多数据源适配

### 9.1 Calcite

Calcite 有 JDBC Adapter，也有多种 Schema Adapter。

优点：

- 适合做统一 SQL 语义层
- 适合把不同数据源能力建模进统一规划框架

但它不会替你完成所有数据库方言落地。

你仍需自己处理：

- 标识符差异
- 函数差异
- Limit / Pagination 方言
- 类型映射差异
- Join / 子查询 / 聚合的下推 SQL 生成

### 9.2 Trino

Trino 在这方面比 Calcite 更“产品化”。

因为：

- 它已经有大量现成 Connector
- 每个 Connector 都内置一定的方言/能力处理

但问题在于：

- 这些处理是 Trino 平台内部的一部分
- 你难以像操作一个嵌入式库那样，把所有行为精确揉进自己的服务编排层

### 9.3 Flink

Flink JDBC 文档明确给出多种数据库的数据类型映射表，说明它对异构数据库并非没有处理。

但从平台目标看，Flink 的重点仍然不是“为联邦查询做最细粒度 SQL 方言兼容层”，而是“让这些表能进入 Flink SQL 体系并参与计算”。

## 10. 执行模型对比

### 10.1 Calcite

执行模型由你决定。

这对本项目是巨大优势：

- 你可以保持 Spring Boot 单服务形态
- 你可以先做同步 JDBC 执行
- 你可以按需扩展并行子查询
- 你可以先做简单结果整合，再逐步增强

这非常符合稳扎稳打的瀑布式实施方法。

### 10.2 Trino

Trino 是标准的分布式 coordinator / worker 查询执行架构。

优点：

- 联邦查询执行能力完整
- 并行执行成熟
- 平台级查询能力很强

缺点：

- 运行时更重
- 部署运维更复杂
- 与当前服务化架构割裂较明显

### 10.3 Flink

Flink 是流批一体运行时。

这意味着：

- 跨源 Join 常在 Flink Runtime 内执行
- 引入状态、Checkpoint、作业生命周期等概念

如果只是为了做数据服务平台联邦查询，这个运行时通常偏重。

## 11. 平台化能力对比

### 11.1 Calcite

Calcite 本身几乎不提供“平台能力”。

它不给你现成的：

- Catalog 管理控制台
- 查询网关
- 结果集 API
- 多租户控制
- 权限体系
- 作业管理

但这恰好适合本项目：

- 因为本项目本来就要自己做“数据服务平台”
- 我们要的是内核，不是替代整个平台

### 11.2 Trino

Trino 具备非常强的平台级查询能力：

- 多 Catalog
- 多 Connector
- EXPLAIN
- 成本优化
- 客户端/JDBC/BI 接入

但它并不直接等于：

- 数据服务配置平台
- 服务发布平台
- API 编排平台

因此若选 Trino，外围仍需自己做：

- 服务注册
- 服务版本
- 参数模板
- 输出字段治理
- 缓存
- 审计
- 前端配置界面

### 11.3 Flink

Flink 的平台能力主要体现在：

- SQL Gateway
- Table API / SQL
- 统一运行平台

但它的产品心智更偏：

- 数据处理平台
- SQL 计算平台

而不是：

- 配置驱动的数据服务平台

## 12. 可嵌入性与二次开发适配性

### 12.1 Calcite

最强。

原因：

- 本来就是 Java 库
- 直接嵌入 Spring Boot
- 可在代码层精细控制 parse / validate / transform 流程
- 最适合与本项目自己的元数据表、发布链路、缓存链路融合

### 12.2 Trino

较弱。

你一般不会把 Trino 当作一个轻量 Java SDK 嵌入现有服务中做核心执行链路，而是：

- 部署 Trino 集群
- 通过 JDBC/HTTP 调用
- 把它作为外部查询平台使用

### 12.3 Flink

中等。

可以嵌入 `TableEnvironment`，也可以走 SQL Gateway。

但其实际使用成本不低，因为：

- 不是单纯 parser/planner 库
- 很多能力与作业运行时绑定

## 13. 工程复杂度对比

### 13.1 Calcite

前期复杂度：

- 中

长期复杂度：

- 可控

原因：

- 你要自己做的东西多
- 但你按项目节奏逐步建设即可
- 每一步都能贴合项目目标，不会引入过重平台

### 13.2 Trino

前期复杂度：

- 高

长期复杂度：

- 中高

原因：

- 部署成熟，但平台引入成本高
- 与当前系统架构集成成本高
- 需要额外治理运行、权限、资源隔离、查询管控

### 13.3 Flink

前期复杂度：

- 高

长期复杂度：

- 高

原因：

- 运行时复杂
- SQL 与作业生命周期管理复杂
- 若只是查询服务，会产生明显架构过载

## 14. 对本项目的适配性评分

评分标准：1 分最低，5 分最高。

| 维度 | Calcite | Trino | Flink |
| --- | --- | --- | --- |
| 嵌入现有 Spring 服务 | 5 | 2 | 3 |
| 单服务维护一条联邦 SQL | 5 | 3 | 3 |
| Join Reorder 可建设性 | 5 | 5 | 3 |
| Filter / Project Pushdown 可建设性 | 5 | 5 | 3 |
| SQL 方言兼容可控性 | 5 | 3 | 3 |
| 自助式数据服务平台适配性 | 5 | 3 | 2 |
| 运行时轻量性 | 5 | 2 | 1 |
| 分阶段实施友好度 | 5 | 2 | 2 |
| 流批一体实时计算能力 | 2 | 2 | 5 |
| 开箱即用联邦查询平台能力 | 2 | 5 | 3 |

总体判断：

- `Calcite`：最适合本项目当前路线
- `Trino`：最适合另起一套独立联邦查询平台
- `Flink`：最适合实时流批处理与实时关联平台

## 15. 推荐技术路线

### 15.1 当前推荐路线

继续采用：

- `Apache Calcite` 作为联邦 SQL 解析、校验、逻辑计划、规则优化核心
- 平台自研：
  - 数据源能力模型
  - 统计信息模型
  - 方言适配层
  - 子计划拆分器
  - JDBC 执行层
  - 结果整合器
  - 计划缓存
  - EXPLAIN / 诊断展示

### 15.2 中期增强建议

建议借鉴 Trino 与 Flink 的优点，而不是整体替换：

- 借鉴 Trino：
  - Connector 能力建模
  - Pushdown 清单化
  - Cost-based Join Reorder 策略
  - EXPLAIN / Plan 诊断方式
- 借鉴 Flink：
  - Catalog 抽象
  - Source Ability 思路
  - SQL Gateway 思路
  - 动态过滤 / Runtime Filter 思路

### 15.3 未来可触发重新选型的条件

出现以下情况时，可重新评估是否引入 Trino 或 Flink：

- 数据服务系统演进为统一分析查询平台，且独立查询集群可接受
- 查询吞吐、并行执行、复杂分析 SQL 需求显著上升
- 需要大量现成 Connector 能力，而不希望自研过多 Adapter
- 需求重心转向实时流处理、CDC 关联、持续计算、SQL Gateway 平台

## 16. 最终建议

最终建议明确如下：

- 不建议当前把 Flink 作为联邦查询执行引擎引入本项目
- 不建议当前把 Trino 作为底座整体替换现有服务化路线
- 建议坚持 `Calcite + 平台执行编排层` 路线

原因很简单：

- 这条路线最贴合本项目“数据服务平台”定位
- 最贴合“一个服务直接维护一条联邦 SQL”的目标
- 最贴合“自助式 SQL 数据服务平台”的中期演进目标
- 最贴合瀑布式、分阶段、稳扎稳打的实施方法

## 17. 参考资料

### 17.1 Apache Flink 官方资料

1. Flink SQL Overview  
   https://nightlies.apache.org/flink/flink-docs-master/docs/sql/overview/

2. SQL Gateway Overview  
   https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/table/sql-gateway/overview/

3. JDBC SQL Connector  
   https://nightlies.apache.org/flink/flink-docs-stable/docs/connectors/table/jdbc/

4. Table & SQL Configuration  
   https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/table/config/

5. SQL Joins  
   https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/table/sql/queries/joins/

6. EXPLAIN Statements  
   https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/table/sql/explain/

7. FLIP-95: New TableSource and TableSink interfaces  
   https://cwiki.apache.org/confluence/display/FLINK/FLIP-95%3A%2BNew%2BTableSource%2Band%2BTableSink%2Binterfaces

### 17.2 Apache Calcite 官方资料

1. Adapters  
   https://calcite.apache.org/docs/adapter

2. Algebra  
   https://calcite.apache.org/docs/algebra.html

3. Planner API  
   https://calcite.apache.org/javadocAggregate/org/apache/calcite/tools/Planner.html

4. Frameworks API  
   https://calcite.apache.org/javadocAggregate/org/apache/calcite/tools/Frameworks.html

### 17.3 Trino 官方资料

1. Cost-based optimizations  
   https://trino.io/docs/current/optimizer/cost-based-optimizations.html

2. Optimizer properties  
   https://trino.io/docs/current/admin/properties-optimizer.html

3. Pushdown  
   https://trino.io/docs/current/optimizer/pushdown.html

4. Connector development  
   https://trino.io/docs/current/develop/connectors.html
