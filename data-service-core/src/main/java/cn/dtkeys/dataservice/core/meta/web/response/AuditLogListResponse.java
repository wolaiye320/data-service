package cn.dtkeys.dataservice.core.meta.web.response;

import java.util.List;

public record AuditLogListResponse(
        int page,
        int size,
        long total,
        List<AuditLogSummaryResponse> items
) {
}
