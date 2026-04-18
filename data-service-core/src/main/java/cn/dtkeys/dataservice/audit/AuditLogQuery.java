package cn.dtkeys.dataservice.audit;

import java.time.LocalDateTime;

/**
 * 审计检索条件。
 */
public record AuditLogQuery(Long serviceId,
                            Long connectionId,
                            String operator,
                            String eventType,
                            String operationResult,
                            LocalDateTime startTime,
                            LocalDateTime endTime,
                            int pageNo,
                            int pageSize) {

    public int offset() {
        return Math.max(pageNo - 1, 0) * pageSize;
    }
}
