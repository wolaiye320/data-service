package cn.dtkeys.dataservice.federation.model;

import java.util.Map;
import java.util.List;

public record FederatedPlan(
    String originalSql,
    List<FederatedPlanStage> stages,
    List<String> logicalPlan,
    List<String> optimizationDecisions,
    Map<String, Object> statisticsSummary,
    Map<String, Object> costSummary,
    String datasourceScope,
    Map<String, Object> executionProfile
) {
}
