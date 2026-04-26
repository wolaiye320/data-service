package cn.dtkeys.dataservice.core.meta.web.response;

import java.util.List;
import java.util.Map;

public record ServicePreviewResponse(
        Long serviceId,
        Integer draftVersion,
        Map<String, Object> previewParams,
        Map<String, Object> requestContext,
        String planSnapshotJson,
        PlanSnapshotViewResponse planSnapshot,
        List<Map<String, Object>> rows,
        Long elapsedMs,
        Map<String, Object> diagnosticSummary
) {
}
