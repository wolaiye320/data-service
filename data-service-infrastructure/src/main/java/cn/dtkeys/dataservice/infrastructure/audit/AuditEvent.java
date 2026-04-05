package cn.dtkeys.dataservice.infrastructure.audit;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 统一审计事件模型。
 */
public record AuditEvent(String eventType,
                         Long serviceId,
                         Long connectionId,
                         String targetType,
                         String targetId,
                         String operator,
                         String operatorRole,
                         String operationResult,
                         String changeSummary,
                         Map<String, Object> detail,
                         OffsetDateTime occurredAt) {

    public static AuditEvent of(String eventType,
                                Long serviceId,
                                Long connectionId,
                                String targetType,
                                String targetId,
                                String operator,
                                String operatorRole,
                                String operationResult,
                                String changeSummary,
                                Map<String, Object> detail) {
        return new AuditEvent(
            eventType,
            serviceId,
            connectionId,
            targetType,
            targetId,
            operator,
            operatorRole,
            operationResult,
            changeSummary,
            detail,
            OffsetDateTime.now()
        );
    }
}
