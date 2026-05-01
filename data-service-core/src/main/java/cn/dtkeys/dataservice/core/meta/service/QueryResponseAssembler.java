package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.FederatedQueryExecutionException;
import cn.dtkeys.dataservice.core.error.QueryParamValidationException;
import cn.dtkeys.dataservice.core.error.ResourceProtectionException;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import cn.dtkeys.dataservice.core.meta.web.request.QueryRequest;
import cn.dtkeys.dataservice.core.meta.web.response.QueryResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 统一组装正式查询响应，保证单项成功、单项失败和批量元信息结构一致。
 */
@Service
public class QueryResponseAssembler {

    /**
     * 组装批量查询总响应。
     */
    public QueryResponse successResponse(DsServiceRecord service,
                                         DsServiceVersionRecord version,
                                         QueryRequestContextService.NormalizedQueryRequestContext requestContext,
                                         List<QueryResponse.QueryResultItem> items,
                                         long elapsedMs) {
        int successCount = (int) items.stream().filter(item -> "SUCCESS".equals(item.status())).count();
        int failureCount = items.size() - successCount;
        boolean cacheHit = items.stream()
                .filter(item -> item.meta() != null)
                .anyMatch(item -> item.meta().cacheHit());
        return new QueryResponse(
                service.getServiceCode(),
                version.getVersion(),
                resolveBatchStatus(successCount, failureCount),
                new QueryResponse.QueryRequestContextView(
                        requestContext.tenantId(),
                        requestContext.callerId(),
                        requestContext.traceId(),
                        requestContext.contextKeys(),
                        requestContext.contextSummaryKeys()
                ),
                items,
                new QueryResponse.QueryResponseMeta(
                        items.size() > 1,
                        items.size(),
                        successCount,
                        failureCount,
                        elapsedMs,
                        cacheHit,
                        requestContext.traceId()
                )
        );
    }

    /**
     * 组装单项成功结果。
     */
    public QueryResponse.QueryResultItem successItem(int index,
                                                     List<Map<String, Object>> rows,
                                                     long elapsedMs,
                                                     boolean cacheHit,
                                                     Map<String, Object> diagnosticSummary) {
        return new QueryResponse.QueryResultItem(
                index,
                "SUCCESS",
                rows,
                null,
                itemMeta(elapsedMs, cacheHit, diagnosticSummary)
        );
    }

    /**
     * 把参数校验失败转换为单项失败结果。
     */
    public QueryResponse.QueryResultItem failureItemFromParamValidation(int index,
                                                                        QueryParamValidationException ex) {
        List<QueryResponse.QueryErrorDetail> details = ex.getDiagnostics().stream()
                .map(diagnostic -> new QueryResponse.QueryErrorDetail(
                        "inputs[" + index + "].params." + diagnostic.paramName(),
                        diagnostic.reasonCode(),
                        diagnostic.paramName(),
                        diagnostic.message(),
                        diagnostic.expectedType(),
                        diagnostic.actualType(),
                        diagnostic.collection()
                ))
                .toList();
        return failureItem(
                index,
                ex,
                details,
                Map.of(
                        "failureStage", "PARAM_VALIDATION",
                        "detailCount", details.size()
                )
        );
    }

    /**
     * 把联邦执行失败转换为单项失败结果。
     */
    public QueryResponse.QueryResultItem failureItemFromFederatedExecution(int index,
                                                                           FederatedQueryExecutionException ex) {
        List<QueryResponse.QueryErrorDetail> details = ex.getDiagnostics().stream()
                .map(diagnostic -> new QueryResponse.QueryErrorDetail(
                        diagnostic.path(),
                        diagnostic.reasonCode(),
                        null,
                        diagnostic.message(),
                        null,
                        diagnostic.connectionCode(),
                        false
                ))
                .toList();
        return failureItem(
                index,
                ex,
                details,
                Map.of(
                        "failureStage", "FEDERATED_EXECUTION",
                        "detailCount", details.size()
                )
        );
    }

    /**
     * 把通用业务异常转换为单项失败结果。
     */
    public QueryResponse.QueryResultItem failureItemFromBusinessException(int index,
                                                                          DataServiceException ex) {
        if (ex instanceof ResourceProtectionException resourceProtectionException) {
            return failureItem(index, ex, List.of(), resourceProtectionException.getDiagnosticSummary());
        }
        return failureItem(
                index,
                ex,
                List.of(),
                Map.of(
                        "failureStage", "QUERY_SERVICE",
                        "detailCount", 0
                )
        );
    }

    private QueryResponse.QueryResultItem failureItem(int index,
                                                      DataServiceException ex,
                                                      List<QueryResponse.QueryErrorDetail> details,
                                                      Map<String, Object> diagnosticSummary) {
        return new QueryResponse.QueryResultItem(
                index,
                "FAILURE",
                List.of(),
                new QueryResponse.QueryError(ex.getErrorCode().name(), ex.getMessage(), details),
                itemMeta(null, false, diagnosticSummary)
        );
    }

    private QueryResponse.QueryResultMeta itemMeta(Long elapsedMs,
                                                   boolean cacheHit,
                                                   Map<String, Object> diagnosticSummary) {
        return new QueryResponse.QueryResultMeta(elapsedMs, cacheHit, diagnosticSummary);
    }

    private String resolveBatchStatus(int successCount, int failureCount) {
        if (failureCount == 0) {
            return "SUCCESS";
        }
        if (successCount == 0) {
            return "FAILURE";
        }
        return "PARTIAL_FAILURE";
    }
}
