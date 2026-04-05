package cn.dtkeys.dataservice.application.service;

import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.infrastructure.audit.AuditLogQuery;
import cn.dtkeys.dataservice.infrastructure.audit.AuditLogQueryResult;
import cn.dtkeys.dataservice.infrastructure.repository.DSAuditLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 审计日志检索服务。
 */
@Service
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
public class AuditLogQueryService {

    private final DSAuditLogRepository dsAuditLogRepository;

    public AuditLogQueryService(DSAuditLogRepository dsAuditLogRepository) {
        this.dsAuditLogRepository = dsAuditLogRepository;
    }

    public AuditLogQueryResult search(AuditLogQuery query) {
        if (query.pageNo() <= 0) {
            throw new ParamInvalidException("pageNo 必须大于 0");
        }
        if (query.pageSize() <= 0 || query.pageSize() > 100) {
            throw new ParamInvalidException("pageSize 必须在 1 到 100 之间");
        }
        long total = dsAuditLogRepository.count(
            query.serviceId(),
            query.connectionId(),
            query.operator(),
            query.eventType(),
            query.operationResult(),
            query.startTime(),
            query.endTime()
        );
        return new AuditLogQueryResult(
            total,
            dsAuditLogRepository.search(
                query.serviceId(),
                query.connectionId(),
                query.operator(),
                query.eventType(),
                query.operationResult(),
                query.startTime(),
                query.endTime(),
                query.pageSize(),
                query.offset()
            )
        );
    }
}
