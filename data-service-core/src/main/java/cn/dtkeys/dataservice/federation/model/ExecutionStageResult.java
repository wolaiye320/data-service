package cn.dtkeys.dataservice.federation.model;

import java.util.List;
import java.util.Map;

public record ExecutionStageResult(
    String stageId,
    String source,
    String executedSql,
    List<Map<String, Object>> rows,
    Map<String, Object> executionSummary
) {
}
