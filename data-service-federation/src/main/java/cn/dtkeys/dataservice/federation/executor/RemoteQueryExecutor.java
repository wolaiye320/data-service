package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.federation.model.ExecutionStageResult;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RemoteQueryExecutor {

    public ExecutionStageResult execute(FederatedPlanStage stage) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stageId", stage.stageId());
        row.put("source", stage.source());
        row.put("sql", stage.sql());
        row.put("projectedFields", stage.projectedFields());
        row.put("filter", stage.pushedFilter());
        return new ExecutionStageResult(stage.stageId(), stage.source(), List.of(row));
    }
}
