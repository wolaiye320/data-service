package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.domain.DsAuditLogRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsAuditLogRepository;
import cn.dtkeys.dataservice.core.meta.web.request.QueryRequest;
import cn.dtkeys.dataservice.core.meta.web.response.QueryResponse;
import cn.dtkeys.dataservice.core.meta.web.support.OperatorContext;
import cn.dtkeys.dataservice.core.security.AuditSensitiveDataMasker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final DsAuditLogRepository auditLogRepository;
    private final QueryAuditSummaryService queryAuditSummaryService;
    private final AuditSensitiveDataMasker auditSensitiveDataMasker;

    public AuditService(DsAuditLogRepository auditLogRepository,
                        QueryAuditSummaryService queryAuditSummaryService,
                        AuditSensitiveDataMasker auditSensitiveDataMasker) {
        this.auditLogRepository = auditLogRepository;
        this.queryAuditSummaryService = queryAuditSummaryService;
        this.auditSensitiveDataMasker = auditSensitiveDataMasker;
    }

    /**
     * 记录连接管理审计。
     */
    public void recordConnectionEvent(Long connectionId,
                                      String eventType,
                                      String targetId,
                                      String operationResult,
                                      String changeSummary,
                                      String detailJson,
                                      OperatorContext operatorContext,
                                      String traceId) {
        DsAuditLogRecord record = new DsAuditLogRecord();
        record.setConnectionId(connectionId);
        record.setEventType(eventType);
        record.setTargetType("CONNECTION");
        record.setTargetId(targetId);
        record.setOperator(operatorContext.operator());
        record.setOperatorRole(operatorContext.operatorRole());
        record.setOperationResult(operationResult);
        record.setTraceId(traceId);
        record.setRequestIp(operatorContext.requestIp());
        record.setChangeSummary(changeSummary);
        record.setDetailJson(auditSensitiveDataMasker.maskStructuredText(detailJson));
        record.setContextSummaryJson(auditSensitiveDataMasker.maskStructuredText("{\"traceId\":\"" + traceId + "\"}"));
        record.setCreatedBy(operatorContext.operator());
        auditLogRepository.insert(record);
    }

    /**
     * 记录管理接口权限失败审计。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAdminAccessDenied(String eventType,
                                        String targetType,
                                        String targetId,
                                        String detailJson,
                                        OperatorContext operatorContext,
                                        String traceId) {
        DsAuditLogRecord record = new DsAuditLogRecord();
        record.setEventType(eventType);
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setOperator(operatorContext == null ? "anonymous" : operatorContext.operator());
        record.setOperatorRole(operatorContext == null ? "ANONYMOUS" : operatorContext.operatorRole());
        record.setOperationResult("FAILURE");
        record.setTraceId(trimTraceId(traceId));
        record.setRequestIp(operatorContext == null ? null : operatorContext.requestIp());
        record.setChangeSummary("管理接口权限拒绝");
        record.setDetailJson(auditSensitiveDataMasker.maskStructuredText(detailJson));
        record.setContextSummaryJson(
                auditSensitiveDataMasker.maskStructuredText("{\"traceId\":\"" + trimTraceId(traceId) + "\"}")
        );
        record.setCreatedBy(operatorContext == null ? "system" : operatorContext.operator());
        auditLogRepository.insert(record);
    }

    /**
     * 记录服务管理审计。
     */
    public void recordServiceEvent(Long serviceId,
                                   String eventType,
                                   String targetId,
                                   String operationResult,
                                   String changeSummary,
                                   String detailJson,
                                   OperatorContext operatorContext,
                                   String traceId) {
        DsAuditLogRecord record = new DsAuditLogRecord();
        record.setServiceId(serviceId);
        record.setEventType(eventType);
        record.setTargetType("SERVICE");
        record.setTargetId(targetId);
        record.setOperator(operatorContext.operator());
        record.setOperatorRole(operatorContext.operatorRole());
        record.setOperationResult(operationResult);
        record.setTraceId(traceId);
        record.setRequestIp(operatorContext.requestIp());
        record.setChangeSummary(changeSummary);
        record.setDetailJson(auditSensitiveDataMasker.maskStructuredText(detailJson));
        record.setContextSummaryJson(auditSensitiveDataMasker.maskStructuredText("{\"traceId\":\"" + traceId + "\"}"));
        record.setCreatedBy(operatorContext.operator());
        auditLogRepository.insert(record);
    }

    /**
     * 以独立事务记录服务管理审计，避免主事务回滚时丢失失败审计。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordServiceEventRequiresNew(Long serviceId,
                                              String eventType,
                                              String targetId,
                                              String operationResult,
                                              String changeSummary,
                                              String detailJson,
                                              OperatorContext operatorContext,
                                              String traceId) {
        recordServiceEvent(
                serviceId,
                eventType,
                targetId,
                operationResult,
                changeSummary,
                detailJson,
                operatorContext,
                trimTraceId(traceId)
        );
    }

    /**
     * 记录查询接口租户校验失败审计。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordQueryTenantAccessDenied(Long serviceId,
                                              String targetId,
                                              String requiredTenantId,
                                              String actualTenantId,
                                              String callerId,
                                              String traceId) {
        DsAuditLogRecord record = new DsAuditLogRecord();
        record.setServiceId(serviceId);
        record.setEventType("QUERY_TENANT_ACCESS_DENIED");
        record.setTargetType("SERVICE");
        record.setTargetId(targetId);
        record.setOperator(callerId == null || callerId.isBlank() ? "anonymous" : callerId);
        record.setOperatorRole("CALLER");
        record.setOperationResult("FAILURE");
        record.setTraceId(trimTraceId(traceId));
        record.setChangeSummary("查询接口租户校验拒绝");
        record.setDetailJson(auditSensitiveDataMasker.maskStructuredText(
                queryAuditSummaryService.writeTenantDeniedDetail(requiredTenantId, actualTenantId, callerId)
        ));
        record.setContextSummaryJson(
                auditSensitiveDataMasker.maskStructuredText(
                        queryAuditSummaryService.writeTenantDeniedContextSummary(
                                requiredTenantId,
                                actualTenantId,
                                callerId,
                                trimTraceId(traceId)
                        )
                )
        );
        record.setCreatedBy(record.getOperator());
        auditLogRepository.insert(record);
    }

    /**
     * 以独立事务记录正式查询审计。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordQueryExecution(Long serviceId,
                                     String serviceCode,
                                     Integer serviceVersion,
                                     QueryRequest request,
                                     QueryRequestContextService.NormalizedQueryRequestContext requestContext,
                                     QueryResponse response,
                                     Throwable error,
                                     long elapsedMs,
                                     String traceId) {
        DsAuditLogRecord record = new DsAuditLogRecord();
        record.setServiceId(serviceId);
        record.setEventType("QUERY_EXECUTE");
        record.setTargetType("SERVICE");
        record.setTargetId(serviceCode);
        record.setOperator(resolveQueryOperator(requestContext));
        record.setOperatorRole("CALLER");
        record.setOperationResult(resolveQueryOperationResult(response, error));
        record.setTraceId(trimTraceId(resolveQueryTraceId(requestContext, traceId)));
        record.setChangeSummary(error == null ? "正式查询完成" : "正式查询失败");
        record.setDetailJson(
                auditSensitiveDataMasker.maskStructuredText(
                        queryAuditSummaryService.writeExecutionDetail(
                                serviceCode == null ? null : buildServiceStub(serviceId, serviceCode),
                                serviceVersion == null ? null : buildVersionStub(serviceId, serviceVersion),
                                request,
                                requestContext,
                                response,
                                error,
                                elapsedMs
                        )
                )
        );
        record.setContextSummaryJson(
                auditSensitiveDataMasker.maskStructuredText(
                        queryAuditSummaryService.writeExecutionContextSummary(
                                request,
                                requestContext,
                                response,
                                error,
                                trimTraceId(traceId)
                        )
                )
        );
        record.setCreatedBy(record.getOperator());
        auditLogRepository.insert(record);
    }

    private cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord buildServiceStub(Long serviceId, String serviceCode) {
        cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord service = new cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord();
        service.setId(serviceId);
        service.setServiceCode(serviceCode);
        return service;
    }

    private cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord buildVersionStub(Long serviceId, Integer serviceVersion) {
        cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord version = new cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord();
        version.setServiceId(serviceId);
        version.setVersion(serviceVersion);
        return version;
    }

    private String resolveQueryOperator(QueryRequestContextService.NormalizedQueryRequestContext requestContext) {
        if (requestContext == null || requestContext.callerId() == null || requestContext.callerId().isBlank()) {
            return "anonymous";
        }
        return requestContext.callerId();
    }

    private String resolveQueryOperationResult(QueryResponse response, Throwable error) {
        if (error != null) {
            return "FAILURE";
        }
        if (response == null || "SUCCESS".equals(response.status())) {
            return "SUCCESS";
        }
        return "FAILURE";
    }

    private String resolveQueryTraceId(QueryRequestContextService.NormalizedQueryRequestContext requestContext, String traceId) {
        if (requestContext != null && requestContext.traceId() != null && !requestContext.traceId().isBlank()) {
            return requestContext.traceId();
        }
        return traceId;
    }

    private String trimTraceId(String traceId) {
        if (traceId == null || traceId.length() <= 64) {
            return traceId;
        }
        return traceId.substring(0, 64);
    }
}
