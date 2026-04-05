package cn.dtkeys.dataservice.infrastructure.audit;

import cn.dtkeys.dataservice.domain.model.DSAuditLog;

import java.util.List;

/**
 * 审计分页查询结果。
 */
public record AuditLogQueryResult(long total, List<DSAuditLog> records) {
}
