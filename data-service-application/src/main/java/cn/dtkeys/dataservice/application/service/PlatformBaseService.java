package cn.dtkeys.dataservice.application.service;

import cn.dtkeys.dataservice.common.context.TraceContext;
import cn.dtkeys.dataservice.common.context.OperatorContext;
import cn.dtkeys.dataservice.infrastructure.audit.AuditEvent;
import cn.dtkeys.dataservice.infrastructure.audit.AuditLogService;
import cn.dtkeys.dataservice.infrastructure.cache.CacheMetricsCollector;
import cn.dtkeys.dataservice.infrastructure.logging.AuditAction;
import cn.dtkeys.dataservice.infrastructure.protection.ResourceProtectionProperties;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 提供平台基础状态和资源保护信息。
 */
@Service
public class PlatformBaseService {

    private final ResourceProtectionProperties resourceProtectionProperties;
    private final AuditLogService auditLogService;
    private final CacheMetricsCollector cacheMetricsCollector;

    public PlatformBaseService(ResourceProtectionProperties resourceProtectionProperties,
                               AuditLogService auditLogService,
                               CacheMetricsCollector cacheMetricsCollector) {
        this.resourceProtectionProperties = resourceProtectionProperties;
        this.auditLogService = auditLogService;
        this.cacheMetricsCollector = cacheMetricsCollector;
    }

    /**
     * 返回平台运行摘要。
     */
    public PlatformBaseSummary getPlatformBaseSummary() {
        auditLogService.record(AuditEvent.of(
            AuditAction.VIEW_PLATFORM_BASELINE.name(),
            null,
            null,
            "PLATFORM_BASELINE",
            "runtime-baseline",
            OperatorContext.getOperator().orElse("SYSTEM"),
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "查看平台运行基线",
            Map.of("traceId", TraceContext.getTraceId().orElse("N/A"))
        ));
        return new PlatformBaseSummary(
            "UP",
            OffsetDateTime.now(),
            resourceProtectionProperties.toSummary(),
            cacheMetricsCollector.toSummary()
        );
    }

    public record PlatformBaseSummary(String status,
                                      OffsetDateTime serverTime,
                                      Map<String, Object> resourceProtection,
                                      Map<String, Object> cacheMetrics) {
    }
}
