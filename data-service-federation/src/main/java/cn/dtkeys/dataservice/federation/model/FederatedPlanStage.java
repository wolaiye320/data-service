package cn.dtkeys.dataservice.federation.model;

import java.util.List;

public record FederatedPlanStage(
    String stageId,
    String source,
    String sql,
    List<String> projectedFields,
    String pushedFilter,
    boolean remoteExecutable
) {
}
