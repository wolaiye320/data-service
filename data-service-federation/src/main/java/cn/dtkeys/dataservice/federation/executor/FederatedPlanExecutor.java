package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.federation.model.ExecutionStageResult;
import cn.dtkeys.dataservice.federation.model.FederatedExecutionResult;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;

import java.util.ArrayList;
import java.util.List;

public class FederatedPlanExecutor {

    private final PlanSplitter planSplitter = new PlanSplitter();
    private final RemoteQueryExecutor remoteQueryExecutor = new RemoteQueryExecutor();
    private final LocalResultAssembler localResultAssembler = new LocalResultAssembler();

    public FederatedExecutionResult execute(FederatedPlan plan) {
        List<FederatedPlanStage> stages = planSplitter.split(plan);
        List<ExecutionStageResult> stageResults = new ArrayList<>();
        for (FederatedPlanStage stage : stages) {
            stageResults.add(remoteQueryExecutor.execute(stage));
        }
        return new FederatedExecutionResult(stageResults, localResultAssembler.assemble(stageResults));
    }
}
