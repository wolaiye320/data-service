package cn.dtkeys.dataservice.federation.model;

import java.util.List;

public record FederatedPlan(
    String originalSql,
    List<FederatedPlanStage> stages,
    List<String> logicalPlan,
    List<String> optimizationDecisions
) {
}
