package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PlanSplitter {

    public List<FederatedPlanStage> split(FederatedPlan plan) {
        List<FederatedPlanStage> stages = new ArrayList<>(plan.stages());
        stages.sort(Comparator
            .comparingInt((FederatedPlanStage stage) -> stage.dependsOnStageIds().size())
            .thenComparingInt(FederatedPlanStage::concurrencyGroup)
            .thenComparing(FederatedPlanStage::stageId));
        return stages;
    }
}
