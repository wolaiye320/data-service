package cn.dtkeys.dataservice.federation.planner;

import cn.dtkeys.dataservice.federation.capability.DatasourceCapabilityRegistry;
import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;
import cn.dtkeys.dataservice.federation.model.SourceCapability;

import java.util.ArrayList;
import java.util.List;

public class FederatedSqlPlanner {

    private final DatasourceCapabilityRegistry datasourceCapabilityRegistry = new DatasourceCapabilityRegistry();

    public FederatedPlan plan(FederatedParsedQuery query) {
        List<FederatedPlanStage> stages = new ArrayList<>();
        List<String> logicalPlan = new ArrayList<>();
        List<String> decisions = new ArrayList<>();

        logicalPlan.add("SCAN sources=" + String.join(",", query.sourceTables()));

        for (int i = 0; i < query.sourceTables().size(); i++) {
            String source = query.sourceTables().get(i);
            SourceCapability capability = datasourceCapabilityRegistry.resolve(inferSourceType(source));
            String sql = buildStageSql(query, source);
            stages.add(new FederatedPlanStage(
                "stage-" + (i + 1),
                source,
                sql,
                query.selectedFields(),
                capability.filterPushdown() ? query.whereClause() : "",
                true
            ));
            decisions.add("source=" + source + ", filterPushdown=" + capability.filterPushdown()
                + ", projectPushdown=" + capability.projectPushdown());
        }

        if (query.joinQuery()) {
            logicalPlan.add("JOIN strategy=service-side");
            decisions.add("join pushdown requires same source and capability support");
        }

        return new FederatedPlan(query.originalSql(), stages, logicalPlan, decisions);
    }

    private String inferSourceType(String source) {
        String normalized = source.toLowerCase();
        if (normalized.contains("oracle")) {
            return "ORACLE";
        }
        if (normalized.contains("pg") || normalized.contains("postgres")) {
            return "POSTGRESQL";
        }
        if (normalized.contains("mysql")) {
            return "MYSQL";
        }
        return "GENERIC";
    }

    private String buildStageSql(FederatedParsedQuery query, String source) {
        String sql = "SELECT " + String.join(", ", query.selectedFields()) + " FROM " + source;
        if (!query.whereClause().isBlank()) {
            sql = sql + " WHERE " + query.whereClause();
        }
        return sql;
    }
}
