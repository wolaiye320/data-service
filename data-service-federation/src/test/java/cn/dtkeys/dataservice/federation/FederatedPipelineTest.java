package cn.dtkeys.dataservice.federation;

import cn.dtkeys.dataservice.federation.diagnostics.FederatedPlanDiagnosticsService;
import cn.dtkeys.dataservice.federation.executor.FederatedPlanExecutor;
import cn.dtkeys.dataservice.federation.model.FederatedDiagnostics;
import cn.dtkeys.dataservice.federation.model.FederatedExecutionResult;
import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.ValidationResult;
import cn.dtkeys.dataservice.federation.optimizer.FederatedSqlOptimizer;
import cn.dtkeys.dataservice.federation.parser.FederatedSqlParser;
import cn.dtkeys.dataservice.federation.planner.FederatedSqlPlanner;
import cn.dtkeys.dataservice.federation.validator.FederatedSqlValidator;
import org.junit.jupiter.api.Test;

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
        assertThat(executionResult.stageResults()).hasSize(2);
        assertThat(executionResult.mergedRows()).hasSize(2);
        assertThat(diagnostics.stagePlan()).hasSize(2);
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
}
