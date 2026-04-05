package cn.dtkeys.dataservice.federation.model;

import java.util.List;
import java.util.Map;

public record FederatedPlanStage(
    String stageId,
    String source,
    String sourceType,
    String sql,
    List<String> projectedFields,
    String pushedFilter,
    boolean remoteExecutable,
    List<String> dependsOnStageIds,
    boolean dynamicFilterEnabled,
    String dynamicFilterField,
    int concurrencyGroup,
    Map<String, Object> attributes
) {
}
