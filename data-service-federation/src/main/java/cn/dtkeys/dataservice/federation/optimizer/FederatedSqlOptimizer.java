package cn.dtkeys.dataservice.federation.optimizer;

import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FederatedSqlOptimizer {

    public FederatedPlan optimize(FederatedPlan plan) {
        List<FederatedPlanStage> optimizedStages = new ArrayList<>(plan.stages());
        optimizedStages.sort(Comparator.comparing(FederatedPlanStage::source));

        List<String> optimizationDecisions = new ArrayList<>(plan.optimizationDecisions());
        optimizationDecisions.add("join reorder heuristic: sort stages by source name");

        boolean filterPushdownApplied = optimizedStages.stream().anyMatch(stage -> !stage.pushedFilter().isBlank());
        if (filterPushdownApplied) {
            optimizationDecisions.add("filter pushdown applied on eligible stages");
        }

        boolean projectPushdownApplied = optimizedStages.stream().allMatch(stage -> !stage.projectedFields().isEmpty());
        if (projectPushdownApplied) {
            optimizationDecisions.add("project pushdown applied on all stages");
        }

        return new FederatedPlan(plan.originalSql(), optimizedStages, plan.logicalPlan(), optimizationDecisions);
    }
}
