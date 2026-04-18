package cn.dtkeys.dataservice.federation;

import cn.dtkeys.dataservice.federation.diagnostics.FederatedPlanDiagnosticsService;
import cn.dtkeys.dataservice.federation.executor.FederatedExecutionOptions;
import cn.dtkeys.dataservice.federation.executor.FederatedPlanExecutor;
import cn.dtkeys.dataservice.federation.model.FederatedDiagnostics;
import cn.dtkeys.dataservice.federation.model.FederatedExecutionResult;
import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;
import cn.dtkeys.dataservice.federation.model.ValidationResult;
import cn.dtkeys.dataservice.federation.optimizer.FederatedSqlOptimizer;
import cn.dtkeys.dataservice.federation.parser.FederatedSqlParser;
import cn.dtkeys.dataservice.federation.planner.FederatedSqlPlanner;
import cn.dtkeys.dataservice.federation.validator.FederatedSqlValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FederatedPipelineTest {

    private final FederatedSqlParser parser = new FederatedSqlParser();
    private final FederatedSqlValidator validator = new FederatedSqlValidator();
    private final FederatedSqlPlanner planner = new FederatedSqlPlanner();
    private final FederatedSqlOptimizer optimizer = new FederatedSqlOptimizer();
    private final FederatedPlanExecutor executor = new FederatedPlanExecutor();
    private final FederatedPlanDiagnosticsService diagnosticsService = new FederatedPlanDiagnosticsService();

    @Test
    void shouldParseValidatePlanOptimizeExecuteAndDiagnoseFederatedQuery() {
        String sql = "SELECT id, amount FROM mysql_orders o JOIN pg_customers c ON o.customer_id = c.id WHERE amount > 100";

        FederatedParsedQuery query = parser.parse(sql);
        ValidationResult validationResult = validator.validate(query);
        FederatedPlan plan = planner.plan(query);
        FederatedPlan optimizedPlan = optimizer.optimize(plan);
        FederatedExecutionResult executionResult = executor.execute(optimizedPlan);
        FederatedDiagnostics diagnostics = diagnosticsService.buildDiagnostics(optimizedPlan);

        assertThat(query.sourceTables()).containsExactly("mysql_orders", "pg_customers");
        assertThat(query.joinQuery()).isTrue();
        assertThat(validationResult.valid()).isTrue();
        assertThat(validationResult.warnings()).isNotEmpty();
        assertThat(optimizedPlan.stages()).hasSize(2);
        assertThat(optimizedPlan.optimizationDecisions()).anyMatch(item -> item.contains("join reorder"));
        assertThat(optimizedPlan.executionProfile()).containsEntry("dynamicFilterEnabled", true);
        assertThat(executionResult.stageResults()).hasSize(2);
        assertThat(executionResult.mergedRows()).hasSize(2);
        assertThat(executionResult.executionSummary()).containsEntry("executedStageCount", 2);
        assertThat(executionResult.stageResults().get(1).executionSummary()).containsEntry("dynamicFilterEnabled", true);
        assertThat(diagnostics.stagePlan()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(diagnostics.pushdownSummary()).isNotEmpty();
    }

    @Test
    void shouldRejectNonSelectSql() {
        assertThatThrownBy(() -> parser.parse("DELETE FROM orders"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("only SELECT");
    }

    @Test
    void shouldWarnWhenUnsupportedFunctionAppears() {
        FederatedParsedQuery query = parser.parse("SELECT custom_func(name) FROM mysql_users");

        ValidationResult validationResult = validator.validate(query);

        assertThat(validationResult.valid()).isTrue();
        assertThat(validationResult.warnings()).anyMatch(item -> item.contains("CUSTOM_FUNC"));
    }

    @Test
    void shouldParseMultilineFederatedSqlTemplate() {
        FederatedParsedQuery query = parser.parse("""
            select pg_customer.customer_id, pg_customer.customer_name, mysql_order.order_amount
            from pg_customer
            join mysql_order on pg_customer.customer_id = mysql_order.customer_id
            where pg_customer.customer_id = :customerId
            """);

        assertThat(query.selectedFields())
            .containsExactly("pg_customer.customer_id", "pg_customer.customer_name", "mysql_order.order_amount");
        assertThat(query.sourceTables()).containsExactly("pg_customer", "mysql_order");
        assertThat(query.whereClause()).isEqualTo("pg_customer.customer_id = :customerId");
        assertThat(query.joinQuery()).isTrue();
    }

    @Test
    void shouldApplyHeuristicFallbackAndDialectAdaptationWhenStatisticsMissing() {
        String sql = "SELECT id, name || code FROM oracle_staging_customer JOIN mysql_orders ON oracle_staging_customer.id = mysql_orders.customer_id";

        FederatedParsedQuery query = parser.parse(sql);
        FederatedPlan plan = planner.plan(query);
        FederatedPlan optimizedPlan = optimizer.optimize(plan);
        FederatedExecutionResult executionResult = executor.execute(optimizedPlan);

        assertThat(plan.statisticsSummary()).containsEntry("statisticsMissing", true);
        assertThat(optimizedPlan.optimizationDecisions()).anyMatch(item -> item.contains("heuristic fallback"));
        assertThat(optimizedPlan.costSummary().get("joinStrategy")).isEqualTo("LOOKUP");
        assertThat(executionResult.stageResults()).anyMatch(result -> result.executedSql().contains("||"));
        assertThat(executionResult.stageResults()).anyMatch(result ->
            Boolean.TRUE.equals(result.executionSummary().get("dynamicFilterEnabled")) && result.executedSql().contains("IN ("));
    }

    @Test
    void shouldRejectUnsupportedCustomFunctionDuringPlanning() {
        FederatedParsedQuery query = parser.parse("SELECT custom_func(name) FROM oracle_customer");

        assertThatThrownBy(() -> planner.plan(query))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("CUSTOM_FUNC");
    }

    @Test
    void shouldSpillLargeIntermediateResultsAndOnlyKeepPreviewRows() {
        FederatedPlan plan = new FederatedPlan(
            "select id from big_stage",
            List.of(
                new FederatedPlanStage(
                    "stage-1",
                    "big_stage",
                    "POSTGRESQL",
                    "SELECT id FROM big_stage",
                    List.of("id"),
                    "",
                    true,
                    List.of(),
                    false,
                    "id",
                    1,
                    Map.of("simulatedRowCount", 12)
                )
            ),
            List.of("SCAN sources=big_stage"),
            List.of("large result simulation"),
            Map.of("sourceCount", 1, "estimatedRows", 12, "joinQuery", false, "statisticsMissing", false),
            Map.of("estimatedCost", 1, "joinStrategy", "REMOTE_SCAN", "majorCostSource", "remote scan"),
            "big_stage",
            Map.of("maxParallelism", 1)
        );

        FederatedExecutionResult executionResult = executor.execute(
            plan,
            new FederatedExecutionOptions(1, 20, 5, 3)
        );

        assertThat(executionResult.mergedRows()).hasSize(5);
        assertThat(executionResult.executionSummary()).containsKey("resultBuffer");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultBuffer = (Map<String, Object>) executionResult.executionSummary().get("resultBuffer");
        assertThat(resultBuffer.get("totalRowCount")).isEqualTo(12);
        assertThat(resultBuffer.get("previewRowCount")).isEqualTo(5);
        assertThat(resultBuffer.get("spillTriggered")).isEqualTo(true);
        assertThat(resultBuffer.get("spilledRowCount")).isEqualTo(9);
    }

    @Test
    void shouldExecuteSameWaveStagesInParallelAndKeepDependentWaveOrdered() {
        FederatedPlan plan = new FederatedPlan(
            "select * from wave_parallel",
            List.of(
                new FederatedPlanStage(
                    "stage-1",
                    "pg_customer",
                    "POSTGRESQL",
                    "SELECT id FROM pg_customer",
                    List.of("id"),
                    "",
                    true,
                    List.of(),
                    false,
                    "id",
                    1,
                    Map.of("simulatedRowCount", 1)
                ),
                new FederatedPlanStage(
                    "stage-2",
                    "mysql_order",
                    "MYSQL",
                    "SELECT customer_id FROM mysql_order",
                    List.of("customer_id"),
                    "",
                    true,
                    List.of(),
                    false,
                    "customer_id",
                    1,
                    Map.of("simulatedRowCount", 1)
                ),
                new FederatedPlanStage(
                    "stage-3",
                    "oracle_invoice",
                    "ORACLE",
                    "SELECT customer_id FROM oracle_invoice",
                    List.of("customer_id"),
                    "",
                    true,
                    List.of("stage-1", "stage-2"),
                    true,
                    "customer_id",
                    2,
                    Map.of("simulatedRowCount", 1)
                )
            ),
            List.of("SCAN pg_customer", "SCAN mysql_order", "LOOKUP oracle_invoice"),
            List.of("parallel wave simulation"),
            Map.of("sourceCount", 3, "estimatedRows", 3, "joinQuery", true, "statisticsMissing", false),
            Map.of("estimatedCost", 3, "joinStrategy", "LOOKUP", "majorCostSource", "local merge"),
            "pg_customer,mysql_order,oracle_invoice",
            Map.of("maxParallelism", 2)
        );

        FederatedExecutionResult executionResult = executor.execute(
            plan,
            new FederatedExecutionOptions(2, 20, 10, 10)
        );

        assertThat(executionResult.executionSummary()).containsEntry("maxParallelism", 2);
        assertThat(executionResult.executionSummary()).containsEntry("stageWaveCount", 2);
        assertThat(executionResult.executionSummary()).containsEntry("executedStageCount", 3);
        assertThat(executionResult.stageResults()).hasSize(3);
        assertThat(executionResult.stageResults().get(2).executedSql()).contains("customer_id IN (");
        assertThat(executionResult.stageResults().subList(0, 2))
            .extracting(result -> result.executionSummary().get("threadName"))
            .doesNotContainNull();
    }

    @Test
    void shouldSplitWaveByActualDependencyGraphInsteadOfDependencyCountOnly() {
        FederatedPlan plan = new FederatedPlan(
            "select * from dependency_graph",
            List.of(
                new FederatedPlanStage(
                    "stage-1",
                    "pg_customer",
                    "POSTGRESQL",
                    "SELECT id FROM pg_customer",
                    List.of("id"),
                    "",
                    true,
                    List.of(),
                    false,
                    "id",
                    1,
                    Map.of("simulatedRowCount", 1)
                ),
                new FederatedPlanStage(
                    "stage-2",
                    "mysql_order",
                    "MYSQL",
                    "SELECT id FROM mysql_order",
                    List.of("id"),
                    "",
                    true,
                    List.of("stage-1"),
                    true,
                    "id",
                    2,
                    Map.of("simulatedRowCount", 1)
                ),
                new FederatedPlanStage(
                    "stage-3",
                    "oracle_invoice",
                    "ORACLE",
                    "SELECT id FROM oracle_invoice",
                    List.of("id"),
                    "",
                    true,
                    List.of("stage-2"),
                    true,
                    "id",
                    3,
                    Map.of("simulatedRowCount", 1)
                )
            ),
            List.of("SCAN pg_customer", "LOOKUP mysql_order", "LOOKUP oracle_invoice"),
            List.of("dependency graph simulation"),
            Map.of("sourceCount", 3, "estimatedRows", 3, "joinQuery", true, "statisticsMissing", false),
            Map.of("estimatedCost", 3, "joinStrategy", "LOOKUP", "majorCostSource", "dependency ordering"),
            "pg_customer,mysql_order,oracle_invoice",
            Map.of("maxParallelism", 3)
        );

        FederatedExecutionResult executionResult = executor.execute(
            plan,
            new FederatedExecutionOptions(3, 20, 10, 10)
        );

        assertThat(executionResult.executionSummary()).containsEntry("stageWaveCount", 3);
        assertThat(executionResult.stageResults()).extracting(stage -> stage.stageId())
            .containsExactly("stage-1", "stage-2", "stage-3");
        assertThat(executionResult.stageResults().get(1).executedSql()).contains("WHERE id IN ('pg_customer-stage-1-0')");
        assertThat(executionResult.stageResults().get(2).executedSql())
            .contains("WHERE id IN ('mysql_order-stage-2-0')");
    }
}
