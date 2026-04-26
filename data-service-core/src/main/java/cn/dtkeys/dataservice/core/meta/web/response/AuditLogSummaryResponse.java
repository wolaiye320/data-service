package cn.dtkeys.dataservice.core.meta.web.response;

import java.time.LocalDateTime;

public record AuditLogSummaryResponse(
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
        String changeSummary,
        LocalDateTime createdAt,
        String createdBy
) {
}
