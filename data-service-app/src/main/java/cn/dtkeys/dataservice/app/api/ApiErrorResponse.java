package cn.dtkeys.dataservice.app.api;

import java.util.Map;

public record ApiErrorResponse(
        String errorCode,
        String message,
        String traceId,
        Map<String, Object> diagnosticSummary
) {
}
