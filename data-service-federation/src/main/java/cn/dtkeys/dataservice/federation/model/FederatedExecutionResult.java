package cn.dtkeys.dataservice.federation.model;

import java.util.List;
import java.util.Map;

public record FederatedExecutionResult(
    List<ExecutionStageResult> stageResults,
    List<Map<String, Object>> mergedRows,
    Map<String, Object> executionSummary
) {
}
