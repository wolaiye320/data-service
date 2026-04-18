package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.federation.model.ExecutionStageResult;

import java.util.List;
import java.util.Map;

public class LocalResultAssembler {

    public FederatedExecutionBuffer assemble(List<ExecutionStageResult> stageResults,
                                             FederatedExecutionOptions executionOptions) {
        FederatedExecutionBuffer buffer = new FederatedExecutionBuffer(
            executionOptions.previewRows(),
            executionOptions.maxResultRows(),
            executionOptions.inMemoryBudgetRows()
        );
        for (ExecutionStageResult stageResult : stageResults) {
            buffer.appendRows(stageResult.rows());
        }
        return buffer;
    }
}
