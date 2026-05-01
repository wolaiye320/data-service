package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.ResourceNotFoundException;
import cn.dtkeys.dataservice.core.meta.domain.DsAuditLogRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsAuditLogRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsServiceRepository;
import cn.dtkeys.dataservice.core.meta.web.response.AuditLogDetailResponse;
import cn.dtkeys.dataservice.core.meta.web.response.AuditLogListResponse;
import cn.dtkeys.dataservice.core.meta.web.response.AuditLogSummaryResponse;
import cn.dtkeys.dataservice.core.security.AuditSensitiveDataMasker;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提供管理端审计日志的分页查询和详情脱敏查看能力。
 */
@Service
public class AuditLogQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private final DsAuditLogRepository auditLogRepository;
    private final DsServiceRepository serviceRepository;
    private final AuditSensitiveDataMasker auditSensitiveDataMasker;

    public AuditLogQueryService(DsAuditLogRepository auditLogRepository,
                                DsServiceRepository serviceRepository,
                                AuditSensitiveDataMasker auditSensitiveDataMasker) {
        this.auditLogRepository = auditLogRepository;
        this.serviceRepository = serviceRepository;
        this.auditSensitiveDataMasker = auditSensitiveDataMasker;
    }

    /**
     * 按过滤条件分页查询审计日志。
     */
    public AuditLogListResponse list(AuditLogQueryCriteria criteria) {
        int page = normalizePage(criteria.page());
        int size = normalizeSize(criteria.size());
        Long serviceId = resolveServiceId(criteria.serviceCode());
        if (criteria.serviceCode() != null && serviceId == null) {
            return new AuditLogListResponse(page, size, 0, List.of());
        }
        long total = auditLogRepository.countByFilters(
                serviceId,
                normalizeBlank(criteria.operator()),
                normalizeBlank(criteria.eventType()),
                normalizeBlank(criteria.operationResult()),
                normalizeBlank(criteria.traceId()),
                criteria.startAt(),
                criteria.endAt()
        );

        // 列表查询阶段就统一完成服务编码映射和摘要脱敏，避免前端再自行拼装展示字段。
        List<DsAuditLogRecord> records = auditLogRepository.findByFilters(
                serviceId,
                normalizeBlank(criteria.operator()),
                normalizeBlank(criteria.eventType()),
                normalizeBlank(criteria.operationResult()),
                normalizeBlank(criteria.traceId()),
                criteria.startAt(),
                criteria.endAt(),
                (page - 1) * size,
                size
        );
        Map<Long, String> serviceCodeMap = loadServiceCodes(records);
        List<AuditLogSummaryResponse> items = records.stream()
                .map(record -> toSummaryResponse(record, serviceCodeMap.get(record.getServiceId())))
                .toList();
        return new AuditLogListResponse(page, size, total, items);
    }

    /**
     * 查询单条审计日志详情，并对明细 JSON 做脱敏处理。
     */
    public AuditLogDetailResponse detail(Long id) {
        DsAuditLogRecord record = auditLogRepository.findById(id);
        if (record == null) {
            throw new ResourceNotFoundException("审计记录不存在: " + id);
        }
        String serviceCode = record.getServiceId() == null ? null : resolveServiceCode(record.getServiceId());
        return new AuditLogDetailResponse(
                record.getId(),
                record.getServiceId(),
                serviceCode,
                record.getConnectionId(),
                record.getEventType(),
                record.getTargetType(),
                record.getTargetId(),
                record.getOperator(),
                record.getOperatorRole(),
                record.getOperationResult(),
                record.getTraceId(),
                record.getRequestIp(),
                record.getChangeSummary(),
                auditSensitiveDataMasker.maskStructuredText(record.getDetailJson()),
                auditSensitiveDataMasker.maskStructuredText(record.getContextSummaryJson()),
                record.getCreatedAt(),
                record.getCreatedBy()
        );
    }

    private AuditLogSummaryResponse toSummaryResponse(DsAuditLogRecord record, String serviceCode) {
        return new AuditLogSummaryResponse(
                record.getId(),
                record.getServiceId(),
                serviceCode,
                record.getConnectionId(),
                record.getEventType(),
                record.getTargetType(),
                record.getTargetId(),
                record.getOperator(),
                record.getOperatorRole(),
                record.getOperationResult(),
                record.getTraceId(),
                record.getChangeSummary(),
                record.getCreatedAt(),
                record.getCreatedBy()
        );
    }

    private Map<Long, String> loadServiceCodes(List<DsAuditLogRecord> records) {
        List<Long> serviceIds = records.stream()
                .map(DsAuditLogRecord::getServiceId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> serviceCodes = new LinkedHashMap<>();
        for (Long serviceId : serviceIds) {
            String serviceCode = resolveServiceCode(serviceId);
            if (serviceCode != null) {
                serviceCodes.put(serviceId, serviceCode);
            }
        }
        return serviceCodes;
    }

    private Long resolveServiceId(String serviceCode) {
        String normalized = normalizeBlank(serviceCode);
        if (normalized == null) {
            return null;
        }
        DsServiceRecord service = serviceRepository.findByServiceCode(normalized);
        return service == null ? null : service.getId();
    }

    private String resolveServiceCode(Long serviceId) {
        DsServiceRecord service = serviceRepository.findById(serviceId);
        return service == null ? null : service.getServiceCode();
    }

    private int normalizePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 1) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "审计分页参数 page 必须大于等于 1");
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "审计分页参数 size 必须在 1 到 200 之间");
        }
        return size;
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record AuditLogQueryCriteria(
            String serviceCode,
            String operator,
            String eventType,
            String operationResult,
            String traceId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Integer page,
            Integer size
    ) {
    }
}
