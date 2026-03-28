package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.federation.model.ExecutionStageResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LocalResultAssembler {

    public List<Map<String, Object>> assemble(List<ExecutionStageResult> stageResults) {
        List<Map<String, Object>> mergedRows = new ArrayList<>();
        for (ExecutionStageResult stageResult : stageResults) {
            mergedRows.addAll(stageResult.rows());
        }
        return mergedRows;
    }
}
