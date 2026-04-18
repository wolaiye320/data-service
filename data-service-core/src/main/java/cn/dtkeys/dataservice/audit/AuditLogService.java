package cn.dtkeys.dataservice.audit;

import cn.dtkeys.dataservice.common.context.TraceContext;
import cn.dtkeys.dataservice.common.context.OperatorContext;
import cn.dtkeys.dataservice.audit.model.DSAuditLog;
import cn.dtkeys.dataservice.repository.DSAuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前阶段先提供统一审计入口，后续再接 Repository 落库。
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final ObjectMapper objectMapper;
    private final ObjectProvider<DSAuditLogRepository> dsAuditLogRepositoryProvider;

    public AuditLogService(ObjectMapper objectMapper, ObjectProvider<DSAuditLogRepository> dsAuditLogRepositoryProvider) {
        this.objectMapper = objectMapper;
        this.dsAuditLogRepositoryProvider = dsAuditLogRepositoryProvider;
    }

    public void record(AuditEvent auditEvent) {
        Map<String, Object> detail = new LinkedHashMap<>(auditEvent.detail());
        TraceContext.getTraceId().ifPresent(traceId -> detail.putIfAbsent("traceId", traceId));
        try {
            DSAuditLogRepository dsAuditLogRepository = dsAuditLogRepositoryProvider.getIfAvailable();
            if (dsAuditLogRepository != null) {
                dsAuditLogRepository.insert(toAuditLog(auditEvent, detail));
            }
            log.info("audit eventType={} targetType={} targetId={} operator={} result={} detail={}",
                auditEvent.eventType(),
                auditEvent.targetType(),
                auditEvent.targetId(),
                auditEvent.operator(),
                auditEvent.operationResult(),
                objectMapper.writeValueAsString(detail));
        } catch (Exception exception) {
            log.warn("audit_serialize_failed eventType={} targetId={}",
                auditEvent.eventType(), auditEvent.targetId(), exception);
        }
    }

    private DSAuditLog toAuditLog(AuditEvent auditEvent, Map<String, Object> detail) throws JsonProcessingException {
        DSAuditLog auditLog = new DSAuditLog();
        auditLog.setServiceId(auditEvent.serviceId());
        auditLog.setConnectionId(auditEvent.connectionId());
        auditLog.setEventType(auditEvent.eventType());
        auditLog.setTargetType(auditEvent.targetType());
        auditLog.setTargetId(auditEvent.targetId());
        auditLog.setOperator(resolveText(auditEvent.operator(), OperatorContext.getOperator().orElse("SYSTEM")));
        auditLog.setOperatorRole(resolveText(auditEvent.operatorRole(), OperatorContext.getRole().orElse("SYSTEM")));
        auditLog.setOperationResult(auditEvent.operationResult());
        auditLog.setTraceId(TraceContext.getTraceId().orElse(null));
        auditLog.setRequestIp(OperatorContext.getRequestIp().orElse(null));
        auditLog.setChangeSummary(auditEvent.changeSummary());
        auditLog.setDetailJson(objectMapper.writeValueAsString(detail));
        auditLog.setCreatedAt(auditEvent.occurredAt().toLocalDateTime());
        auditLog.setCreatedBy(resolveText(auditEvent.operator(), OperatorContext.getOperator().orElse("SYSTEM")));
        return auditLog;
    }

    private String resolveText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}
