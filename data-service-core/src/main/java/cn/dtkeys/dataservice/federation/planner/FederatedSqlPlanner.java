package cn.dtkeys.dataservice.federation.planner;

import cn.dtkeys.dataservice.federation.capability.DatasourceCapabilityRegistry;
import cn.dtkeys.dataservice.federation.dialect.DialectAdapterRegistry;
import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;
import cn.dtkeys.dataservice.federation.model.SourceCapability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FederatedSqlPlanner {

    private final DatasourceCapabilityRegistry datasourceCapabilityRegistry = new DatasourceCapabilityRegistry();
    private final DialectAdapterRegistry dialectAdapterRegistry = new DialectAdapterRegistry();

    public FederatedPlan plan(FederatedParsedQuery query) {
        List<FederatedPlanStage> stages = new ArrayList<>();
        List<String> logicalPlan = new ArrayList<>();
        List<String> decisions = new ArrayList<>();
        Map<String, Object> executionProfile = new LinkedHashMap<>();

        logicalPlan.add("SCAN sources=" + String.join(",", query.sourceTables()));
        boolean statisticsMissing = query.sourceTables().stream().anyMatch(this::statisticsLikelyMissing);
        if (statisticsMissing) {
            decisions.add("heuristic fallback enabled: statistics missing on at least one source");
        }

        for (int i = 0; i < query.sourceTables().size(); i++) {
            String source = query.sourceTables().get(i);
            String sourceType = inferSourceType(source);
            SourceCapability capability = datasourceCapabilityRegistry.resolve(sourceType);
            String sql = dialectAdapterRegistry.adaptSql(sourceType, buildStageSql(query, source));
            Map<String, Object> stageAttributes = new LinkedHashMap<>(capability.attributes());
            stageAttributes.put("statisticsAvailable", !statisticsMissing);
            stageAttributes.put("heuristicStrategy", resolveHeuristicStrategy(query, capability, statisticsMissing));
            stages.add(new FederatedPlanStage(
                "stage-" + (i + 1),
                source,
                sourceType,
                sql,
                query.selectedFields(),
                capability.filterPushdown() ? query.whereClause() : "",
                true,
                i == 0 ? List.of() : List.of("stage-1"),
                query.joinQuery() && i > 0,
                resolveDynamicFilterField(query),
                i == 0 ? 1 : 2,
                stageAttributes
            ));
            decisions.add("source=" + source + ", filterPushdown=" + capability.filterPushdown()
                + ", projectPushdown=" + capability.projectPushdown());
            if (statisticsMissing) {
                decisions.add("source=" + source + ", heuristic fallback=" + stageAttributes.get("heuristicStrategy"));
            }
        }

        if (query.joinQuery()) {
            logicalPlan.add("JOIN strategy=service-side");
            decisions.add("join pushdown requires same source and capability support");
        }

        Map<String, Object> statisticsSummary = new LinkedHashMap<>();
        statisticsSummary.put("sourceCount", query.sourceTables().size());
        statisticsSummary.put("estimatedRows", statisticsMissing ? null : query.sourceTables().size() * 1000);
        statisticsSummary.put("joinQuery", query.joinQuery());
        statisticsSummary.put("statisticsMissing", statisticsMissing);
        Map<String, Object> costSummary = Map.of(
            "estimatedCost", query.sourceTables().size() * (query.joinQuery() ? 20 : 10),
            "joinStrategy", query.joinQuery()
                ? resolveJoinStrategy(stages, statisticsMissing)
                : "REMOTE_SCAN",
            "majorCostSource", query.joinQuery() ? "cross-source join" : "remote scan"
        );
        executionProfile.put("maxParallelism", query.joinQuery() ? 2 : 1);
        executionProfile.put("dynamicFilterEnabled", query.joinQuery());
        executionProfile.put("statisticsFallback", statisticsMissing);

        return new FederatedPlan(
            query.originalSql(),
            stages,
            logicalPlan,
            decisions,
            statisticsSummary,
            costSummary,
            String.join(",", query.sourceTables()),
            executionProfile
        );
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

    private boolean statisticsLikelyMissing(String source) {
        String normalized = source.toLowerCase();
        return normalized.contains("unknown") || normalized.contains("staging") || normalized.contains("adhoc");
    }

    private String resolveHeuristicStrategy(FederatedParsedQuery query,
                                            SourceCapability capability,
                                            boolean statisticsMissing) {
        if (!statisticsMissing) {
            return "COST_BASED";
        }
        if (!query.joinQuery()) {
            return "REMOTE_SCAN";
        }
        return String.valueOf(capability.attributes().getOrDefault("recommendedJoinStrategy", "LOCAL"));
    }

    private String resolveJoinStrategy(List<FederatedPlanStage> stages, boolean statisticsMissing) {
        if (!statisticsMissing) {
            return "SERVICE_SIDE";
        }
        return stages.stream()
            .map(stage -> String.valueOf(stage.attributes().getOrDefault("heuristicStrategy", "LOCAL")))
            .filter(strategy -> !"COST_BASED".equals(strategy))
            .findFirst()
            .orElse("LOCAL");
    }

    private String resolveDynamicFilterField(FederatedParsedQuery query) {
        return query.selectedFields().stream()
            .map(String::trim)
            .filter(field -> field.contains("."))
            .map(field -> field.substring(field.lastIndexOf('.') + 1))
            .findFirst()
            .orElseGet(() -> query.selectedFields().isEmpty() ? "id" : query.selectedFields().get(0));
    }
}
