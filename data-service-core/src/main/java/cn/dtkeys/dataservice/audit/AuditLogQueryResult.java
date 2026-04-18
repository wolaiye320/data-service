package cn.dtkeys.dataservice.audit;

import cn.dtkeys.dataservice.audit.model.DSAuditLog;

import java.util.List;

/**
 * 审计分页查询结果。
 */
public record AuditLogQueryResult(long total, List<DSAuditLog> records) {
}
