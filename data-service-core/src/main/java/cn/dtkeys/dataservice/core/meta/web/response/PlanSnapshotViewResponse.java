package cn.dtkeys.dataservice.core.meta.web.response;

import java.util.List;

public record PlanSnapshotViewResponse(
        String stage,
        String sqlType,
        int sourceCount,
        int paramCount,
        List<String> stages,
        String diagnosticSummary
) {
}
