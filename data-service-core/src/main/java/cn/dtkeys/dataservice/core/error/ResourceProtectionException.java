package cn.dtkeys.dataservice.core.error;

import java.util.LinkedHashMap;
import java.util.Map;

public class ResourceProtectionException extends DataServiceException {

    private final Map<String, Object> diagnosticSummary;

    public ResourceProtectionException(String message, Map<String, Object> diagnosticSummary) {
        super(ErrorCode.INVALID_ARGUMENT, message);
        this.diagnosticSummary = Map.copyOf(diagnosticSummary);
    }

    public Map<String, Object> getDiagnosticSummary() {
        return diagnosticSummary;
    }

    public static ResourceProtectionException resultRowsExceeded(String stage,
                                                                 int maxResultRows,
                                                                 int actualRows) {
        return new ResourceProtectionException(
                "结果集超过服务上限: stage=" + stage + ", maxResultRows=" + maxResultRows + ", actualRows=" + actualRows,
                orderedSummary(
                        "failureStage", "RESULT_GUARD",
                        "protectionType", "RESULT_ROWS",
                        "guardStage", stage,
                        "maxResultRows", maxResultRows,
                        "actualRows", actualRows,
                        "detailCount", 0
                )
        );
    }

    public static ResourceProtectionException batchSizeExceeded(int maxBatchSize,
                                                                int actualBatchSize) {
        return new ResourceProtectionException(
                "批量请求超过服务上限: maxBatchSize=" + maxBatchSize + ", actualBatchSize=" + actualBatchSize,
                orderedSummary(
                        "failureStage", "BATCH_GUARD",
                        "protectionType", "BATCH_SIZE",
                        "maxBatchSize", maxBatchSize,
                        "actualBatchSize", actualBatchSize
                )
        );
    }

    public static ResourceProtectionException queryTimeoutExceeded(String stage,
                                                                   int timeoutSeconds) {
        return new ResourceProtectionException(
                "查询执行超时: stage=" + stage + ", timeoutSeconds=" + timeoutSeconds,
                orderedSummary(
                        "failureStage", "TIMEOUT_GUARD",
                        "protectionType", "QUERY_TIMEOUT",
                        "guardStage", stage,
                        "timeoutSeconds", timeoutSeconds,
                        "detailCount", 0
                )
        );
    }

    public static ResourceProtectionException concurrentQueriesExceeded(int maxConcurrentQueries) {
        return new ResourceProtectionException(
                "联邦查询并发超过系统上限: maxConcurrentQueries=" + maxConcurrentQueries,
                orderedSummary(
                        "failureStage", "CONCURRENCY_GUARD",
                        "protectionType", "CONCURRENT_QUERIES",
                        "maxConcurrentQueries", maxConcurrentQueries
                )
        );
    }

    private static Map<String, Object> orderedSummary(Object... entries) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            summary.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return summary;
    }
}
