package cn.dtkeys.dataservice.core.meta.web.response;

import java.time.LocalDateTime;

public record AuditLogDetailResponse(
        Long id,
        Long serviceId,
        String serviceCode,
        Long connectionId,
        String eventType,
        String targetType,
        String targetId,
        String operator,
        String operatorRole,
        String operationResult,
        String traceId,
        String requestIp,
        String changeSummary,
        String detailJson,
        String contextSummaryJson,
        LocalDateTime createdAt,
        String createdBy
) {
}
