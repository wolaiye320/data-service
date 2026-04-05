package cn.dtkeys.dataservice.federation.diagnostics;

import cn.dtkeys.dataservice.federation.model.FederatedDiagnostics;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;

import java.util.ArrayList;
import java.util.List;

public class FederatedPlanDiagnosticsService {

    public FederatedDiagnostics buildDiagnostics(FederatedPlan plan) {
        List<String> stagePlan = new ArrayList<>();
        List<String> pushdownSummary = new ArrayList<>();
        List<String> fallbackReasons = new ArrayList<>();

        for (FederatedPlanStage stage : plan.stages()) {
            stagePlan.add(stage.stageId() + ": " + stage.source() + " -> " + stage.sql());
            if (!stage.pushedFilter().isBlank()) {
                pushdownSummary.add(stage.stageId() + ": filter pushdown = " + stage.pushedFilter());
            } else {
                fallbackReasons.add(stage.stageId() + ": filter not pushed down");
            }
            if (stage.dynamicFilterEnabled()) {
                pushdownSummary.add(stage.stageId() + ": dynamic filter field = " + stage.dynamicFilterField());
            }
            if (!stage.dependsOnStageIds().isEmpty()) {
                stagePlan.add(stage.stageId() + ": dependsOn = " + String.join(",", stage.dependsOnStageIds()));
            }
        }

        if (plan.optimizationDecisions().stream().noneMatch(item -> item.contains("project pushdown"))) {
            fallbackReasons.add("project pushdown not applied");
        }
        if (plan.optimizationDecisions().stream().anyMatch(item -> item.contains("heuristic fallback"))) {
            fallbackReasons.add("statistics missing, heuristic fallback enabled");
        }

        return new FederatedDiagnostics(plan.logicalPlan(), stagePlan, pushdownSummary, fallbackReasons);
    }
}
