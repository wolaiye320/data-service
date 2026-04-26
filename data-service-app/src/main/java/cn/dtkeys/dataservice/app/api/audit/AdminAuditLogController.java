package cn.dtkeys.dataservice.app.api.audit;

import cn.dtkeys.dataservice.core.meta.service.AuditLogQueryService;
import cn.dtkeys.dataservice.core.meta.web.response.AuditLogDetailResponse;
import cn.dtkeys.dataservice.core.meta.web.response.AuditLogListResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Validated
@RestController
@RequestMapping("/api/admin/audits")
public class AdminAuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    public AdminAuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    public AuditLogListResponse list(@RequestParam(value = "serviceCode", required = false) String serviceCode,
                                     @RequestParam(value = "operator", required = false) String operator,
                                     @RequestParam(value = "eventType", required = false) String eventType,
                                     @RequestParam(value = "operationResult", required = false) String operationResult,
                                     @RequestParam(value = "traceId", required = false) String traceId,
                                     @RequestParam(value = "startAt", required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                                     @RequestParam(value = "endAt", required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
                                     @RequestParam(value = "page", required = false) Integer page,
                                     @RequestParam(value = "size", required = false) Integer size) {
        return auditLogQueryService.list(
                new AuditLogQueryService.AuditLogQueryCriteria(
                        serviceCode,
                        operator,
                        eventType,
                        operationResult,
                        traceId,
                        startAt,
                        endAt,
                        page,
                        size
                )
        );
    }

    @GetMapping("/{id}")
    public AuditLogDetailResponse detail(@PathVariable("id") Long id) {
        return auditLogQueryService.detail(id);
    }
}
