package cn.dtkeys.dataservice.core.meta.web.response;

import java.util.List;
import java.util.Map;

public record QueryResponse(
        String serviceCode,
        Integer serviceVersion,
        String status,
        QueryRequestContextView requestContext,
        List<QueryResultItem> items,
        QueryResponseMeta meta
) {

    public record QueryResultItem(
            int index,
            String status,
            List<Map<String, Object>> rows,
            QueryError error,
            QueryResultMeta meta
    ) {
    }

    public record QueryResultMeta(
            Long elapsedMs,
            boolean cacheHit,
            Map<String, Object> diagnosticSummary
    ) {
    }

    public record QueryError(
            String errorCode,
            String message,
            List<QueryErrorDetail> details
    ) {
    }

    public record QueryErrorDetail(
            String path,
            String reasonCode,
            String paramName,
            String message,
            String expectedType,
            String actualType,
            boolean collection
    ) {
    }

    public record QueryResponseMeta(
        boolean batch,
        int requestedBatchSize,
        int successCount,
        int failureCount,
        long elapsedMs,
        boolean cacheHit,
        String traceId
    ) {
    }

    public record QueryRequestContextView(
            String tenantId,
            String callerId,
            String traceId,
            List<String> contextKeys,
            List<String> contextSummaryKeys
    ) {
    }
}
