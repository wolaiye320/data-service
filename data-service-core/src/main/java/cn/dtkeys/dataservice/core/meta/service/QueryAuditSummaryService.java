package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.ResourceProtectionException;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import cn.dtkeys.dataservice.core.meta.web.request.QueryRequest;
import cn.dtkeys.dataservice.core.meta.web.response.QueryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 组装正式查询与租户拒绝场景的审计详情和上下文摘要。
 */
@Service
public class QueryAuditSummaryService {

    private final ObjectMapper objectMapper;

    public QueryAuditSummaryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 生成正式查询执行审计详情。
     */
    public String writeExecutionDetail(DsServiceRecord service,
                                       DsServiceVersionRecord version,
                                       QueryRequest request,
                                       QueryRequestContextService.NormalizedQueryRequestContext requestContext,
                                       QueryResponse response,
                                       Throwable error,
                                       long elapsedMs) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("serviceCode", service == null ? request.serviceCode() : service.getServiceCode());
        detail.put("serviceVersion", version == null ? null : version.getVersion());
        detail.put("queryStatus", response == null ? "FAILURE" : response.status());
        detail.put("elapsedMs", elapsedMs);
        detail.put("requestedBatchSize", requestedBatchSize(request));
        detail.put("inputParamKeys", inputParamKeys(request));
        if (response != null && response.meta() != null) {
            detail.put("successCount", response.meta().successCount());
            detail.put("failureCount", response.meta().failureCount());
            detail.put("cacheHit", response.meta().cacheHit());
            detail.put("itemSummaries", response.items().stream().map(this::toItemSummary).toList());
        }
        if (requestContext != null) {
            detail.put("callerId", requestContext.callerId());
            detail.put("tenantId", requestContext.tenantId());
        }
        if (error != null) {
            detail.put("errorCode", errorCodeOf(error));
            detail.put("errorMessage", error.getMessage());
            Map<String, Object> diagnosticSummary = diagnosticSummaryOf(error);
            if (!diagnosticSummary.isEmpty()) {
                detail.put("diagnosticSummary", diagnosticSummary);
            }
        }
        return writeJson(detail, "正式查询审计详情序列化失败");
    }

    /**
     * 生成正式查询执行上下文摘要。
     */
    public String writeExecutionContextSummary(QueryRequest request,
                                               QueryRequestContextService.NormalizedQueryRequestContext requestContext,
                                               QueryResponse response,
                                               Throwable error,
                                               String traceId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("traceId", resolveTraceId(requestContext, traceId));
        summary.put("callerId", requestContext == null ? null : requestContext.callerId());
        summary.put("tenantId", requestContext == null ? null : requestContext.tenantId());
        summary.put("contextKeys", requestContext == null ? List.of() : requestContext.contextKeys());
        summary.put("contextSummaryKeys", requestContext == null ? List.of() : requestContext.contextSummaryKeys());
        summary.put("requestedBatchSize", requestedBatchSize(request));
        summary.put("inputParamKeys", inputParamKeys(request));
        if (response != null && response.meta() != null) {
            summary.put("cacheHit", response.meta().cacheHit());
            summary.put("queryStatus", response.status());
        }
        if (error != null) {
            summary.put("errorCode", errorCodeOf(error));
        }
        return writeJson(summary, "正式查询审计上下文序列化失败");
    }

    /**
     * 生成租户拒绝场景的审计详情。
     */
    public String writeTenantDeniedDetail(String requiredTenantId,
                                          String actualTenantId,
                                          String callerId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("requiredTenantId", requiredTenantId);
        detail.put("actualTenantId", actualTenantId);
        detail.put("callerId", callerId);
        return writeJson(detail, "租户校验审计详情序列化失败");
    }

    /**
     * 生成租户拒绝场景的上下文摘要。
     */
    public String writeTenantDeniedContextSummary(String requiredTenantId,
                                                  String actualTenantId,
                                                  String callerId,
                                                  String traceId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("traceId", traceId);
        summary.put("requiredTenantId", requiredTenantId);
        summary.put("actualTenantId", actualTenantId);
        summary.put("callerId", callerId);
        return writeJson(summary, "租户校验审计上下文序列化失败");
    }

    private Map<String, Object> toItemSummary(QueryResponse.QueryResultItem item) {
        Map<String, Object> itemSummary = new LinkedHashMap<>();
        itemSummary.put("index", item.index());
        itemSummary.put("status", item.status());
        itemSummary.put("rowCount", item.rows() == null ? 0 : item.rows().size());
        itemSummary.put("cacheHit", item.meta() != null && item.meta().cacheHit());
        if (item.error() != null) {
            itemSummary.put("errorCode", item.error().errorCode());
        }
        if (item.meta() != null && item.meta().diagnosticSummary() != null) {
            itemSummary.put("failureStage", item.meta().diagnosticSummary().get("failureStage"));
            itemSummary.put("protectionType", item.meta().diagnosticSummary().get("protectionType"));
        }
        return itemSummary;
    }

    private int requestedBatchSize(QueryRequest request) {
        return request == null || request.inputs() == null ? 0 : request.inputs().size();
    }

    private List<String> inputParamKeys(QueryRequest request) {
        if (request == null || request.inputs() == null || request.inputs().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> paramKeys = new LinkedHashSet<>();
        request.inputs().stream()
                .filter(input -> input != null && input.params() != null)
                .forEach(input -> paramKeys.addAll(input.params().keySet()));
        return paramKeys.stream().toList();
    }

    private String resolveTraceId(QueryRequestContextService.NormalizedQueryRequestContext requestContext, String traceId) {
        if (requestContext != null && requestContext.traceId() != null && !requestContext.traceId().isBlank()) {
            return requestContext.traceId();
        }
        return traceId;
    }

    private String errorCodeOf(Throwable error) {
        if (error instanceof DataServiceException dataServiceException) {
            return dataServiceException.getErrorCode().name();
        }
        return ErrorCode.INTERNAL_ERROR.name();
    }

    private Map<String, Object> diagnosticSummaryOf(Throwable error) {
        if (error instanceof ResourceProtectionException resourceProtectionException) {
            return resourceProtectionException.getDiagnosticSummary();
        }
        return Map.of();
    }

    private String writeJson(Map<String, Object> value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, errorMessage, ex);
        }
    }
}
