package cn.dtkeys.dataservice.interfaces.controller;

import cn.dtkeys.dataservice.application.service.AuditLogQueryService;
import cn.dtkeys.dataservice.domain.model.DSAuditLog;
import cn.dtkeys.dataservice.infrastructure.audit.AuditLogQuery;
import cn.dtkeys.dataservice.infrastructure.audit.AuditLogQueryResult;
import cn.dtkeys.dataservice.infrastructure.repository.DSAuditLogRepository;
import cn.dtkeys.dataservice.infrastructure.security.PermissionCode;
import cn.dtkeys.dataservice.infrastructure.security.RequirePermission;
import cn.dtkeys.dataservice.interfaces.response.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计日志检索接口。
 */
@Validated
@RestController
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;
    private final DSAuditLogRepository dsAuditLogRepository;

    public AuditLogController(AuditLogQueryService auditLogQueryService,
                              DSAuditLogRepository dsAuditLogRepository) {
        this.auditLogQueryService = auditLogQueryService;
        this.dsAuditLogRepository = dsAuditLogRepository;
    }

    @GetMapping
    @RequirePermission(PermissionCode.AUDIT_LOG_LIST)
    public ApiResponse<Map<String, Object>> search(
        @RequestParam(name = "serviceId", required = false) Long serviceId,
        @RequestParam(name = "connectionId", required = false) Long connectionId,
        @RequestParam(name = "operator", required = false) String operator,
        @RequestParam(name = "eventType", required = false) String eventType,
        @RequestParam(name = "operationResult", required = false) String operationResult,
        @RequestParam(name = "startTime", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @RequestParam(name = "endTime", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
        @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
        @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        AuditLogQueryResult queryResult = auditLogQueryService.search(new AuditLogQuery(
            serviceId,
            connectionId,
            operator,
            eventType,
            operationResult,
            startTime,
            endTime,
            pageNo,
            pageSize
        ));
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("total", queryResult.total());
        data.put("records", queryResult.records());
        return ApiResponse.success(data, Map.of("pageNo", pageNo, "pageSize", pageSize));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCode.AUDIT_LOG_DETAIL)
    public ApiResponse<DSAuditLog> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(dsAuditLogRepository.findById(id));
    }
}
