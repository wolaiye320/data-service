package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;

import java.util.List;

public class PlanSplitter {

    public List<FederatedPlanStage> split(FederatedPlan plan) {
        return plan.stages();
    }
}
