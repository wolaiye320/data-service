package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.AccessDeniedException;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import org.springframework.stereotype.Service;

/**
 * 校验正式查询的租户隔离要求，并在拒绝时补充审计。
 */
@Service
public class QueryTenantGuardService {

    private final AuditService auditService;

    public QueryTenantGuardService(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 校验请求租户是否满足服务预留的租户约束。
     */
    public void validate(DsServiceRecord service,
                         QueryRequestContextService.NormalizedQueryRequestContext requestContext) {
        String requiredTenantId = normalize(service == null ? null : service.getTenantId());
        if (requiredTenantId == null) {
            return;
        }
        String actualTenantId = normalize(requestContext == null ? null : requestContext.tenantId());
        if (actualTenantId == null) {
            // 缺少 tenantId 与 tenantId 越权都要先落审计，再返回拒绝。
            recordDenied(service, requestContext, requiredTenantId, null);
            throw new AccessDeniedException("查询接口要求提供租户标识 tenantId");
        }
        if (!requiredTenantId.equals(actualTenantId)) {
            recordDenied(service, requestContext, requiredTenantId, actualTenantId);
            throw new AccessDeniedException("查询接口租户越权: requiredTenantId=" + requiredTenantId + ", actualTenantId=" + actualTenantId);
        }
    }

    private void recordDenied(DsServiceRecord service,
                              QueryRequestContextService.NormalizedQueryRequestContext requestContext,
                              String requiredTenantId,
                              String actualTenantId) {
        if (service == null) {
            return;
        }
        auditService.recordQueryTenantAccessDenied(
                service.getId(),
                service.getServiceCode(),
                requiredTenantId,
                actualTenantId,
                requestContext == null ? null : requestContext.callerId(),
                requestContext == null ? null : requestContext.traceId()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
