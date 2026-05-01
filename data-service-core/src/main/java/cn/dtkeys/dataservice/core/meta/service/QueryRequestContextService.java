package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.web.request.QueryRequestContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 统一规范化正式查询请求中的上下文字段。
 */
@Service
public class QueryRequestContextService {

    /**
     * 规范化调用上下文，并在缺少请求内 traceId 时回退到外部传入值。
     */
    public NormalizedQueryRequestContext normalize(QueryRequestContext requestContext, String fallbackTraceId) {
        String tenantId = normalizeBlank(requestContext == null ? null : requestContext.tenantId());
        String callerId = normalizeBlank(requestContext == null ? null : requestContext.callerId());
        String traceId = normalizeBlank(requestContext == null ? null : requestContext.traceId());
        if (traceId == null) {
            traceId = normalizeBlank(fallbackTraceId);
        }
        List<String> contextKeys = requestContext == null || requestContext.contextKeys() == null
                ? List.of()
                : requestContext.contextKeys().stream()
                .map(this::normalizeBlank)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        LinkedHashSet<String> summaryParts = new LinkedHashSet<>();
        if (tenantId != null) {
            summaryParts.add("tenantId");
        }
        if (callerId != null) {
            summaryParts.add("callerId");
        }
        if (traceId != null) {
            summaryParts.add("traceId");
        }
        summaryParts.addAll(contextKeys);
        return new NormalizedQueryRequestContext(
                tenantId,
                callerId,
                traceId,
                contextKeys,
                summaryParts.stream().toList()
        );
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record NormalizedQueryRequestContext(
            String tenantId,
            String callerId,
            String traceId,
            List<String> contextKeys,
            List<String> contextSummaryKeys
    ) {
    }
}
