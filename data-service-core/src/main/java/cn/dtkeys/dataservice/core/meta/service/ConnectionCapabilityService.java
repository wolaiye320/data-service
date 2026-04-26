package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.ResourceNotFoundException;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsSourceCapabilityRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsConnectionAdminRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsSourceCapabilityRepository;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionCapabilityItemRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionCapabilityUpsertRequest;
import cn.dtkeys.dataservice.core.meta.web.response.ConnectionCapabilityItemResponse;
import cn.dtkeys.dataservice.core.meta.web.response.ConnectionCapabilityResponse;
import cn.dtkeys.dataservice.core.meta.web.support.OperatorContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConnectionCapabilityService {

    private final DsConnectionAdminRepository connectionAdminRepository;
    private final DsSourceCapabilityRepository sourceCapabilityRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ConnectionCapabilityService(DsConnectionAdminRepository connectionAdminRepository,
                                       DsSourceCapabilityRepository sourceCapabilityRepository,
                                       AuditService auditService,
                                       ObjectMapper objectMapper) {
        this.connectionAdminRepository = connectionAdminRepository;
        this.sourceCapabilityRepository = sourceCapabilityRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询连接当前启用的数据源能力定义。
     */
    public ConnectionCapabilityResponse detail(Long connectionId) {
        DsConnectionRecord connection = requireConnection(connectionId);
        return toResponse(connection);
    }

    /**
     * 覆盖保存连接能力定义。
     */
    @Transactional
    public ConnectionCapabilityResponse upsert(Long connectionId,
                                               ConnectionCapabilityUpsertRequest request,
                                               OperatorContext operatorContext,
                                               String traceId) {
        DsConnectionRecord connection = requireConnection(connectionId);
        List<NormalizedCapability> normalizedCapabilities = normalizeCapabilities(request.capabilities());
        sourceCapabilityRepository.deleteByConnectionId(connectionId);
        for (NormalizedCapability capability : normalizedCapabilities) {
            DsSourceCapabilityRecord record = new DsSourceCapabilityRecord();
            record.setConnectionId(connectionId);
            record.setDbType(connection.getDbType());
            record.setCapabilityCode(capability.capabilityCode());
            record.setCapabilityValue(capability.capabilityValue());
            record.setCapabilityDetailJson(capability.capabilityDetailJson());
            record.setScope(capability.scope());
            record.setScopeValue(capability.scopeValue());
            record.setStatus("ENABLED");
            record.setRemark(capability.remark());
            sourceCapabilityRepository.insert(record);
        }
        ConnectionCapabilityResponse response = toResponse(connection);
        auditService.recordConnectionEvent(
                connectionId,
                "UPSERT_CONNECTION_CAPABILITIES",
                connection.getConnectionCode(),
                "SUCCESS",
                "更新连接能力成功",
                buildAuditDetail(response),
                operatorContext,
                traceId
        );
        return response;
    }

    private DsConnectionRecord requireConnection(Long connectionId) {
        DsConnectionRecord connection = connectionAdminRepository.findById(connectionId);
        if (connection == null) {
            throw new ResourceNotFoundException("连接不存在: " + connectionId);
        }
        return connection;
    }

    private List<NormalizedCapability> normalizeCapabilities(List<ConnectionCapabilityItemRequest> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        Set<String> deduplicatedKeys = new LinkedHashSet<>();
        return capabilities.stream()
                .map(this::normalizeCapability)
                .peek(capability -> {
                    if (!deduplicatedKeys.add(capability.uniqueKey())) {
                        throw new DataServiceException(
                                ErrorCode.INVALID_ARGUMENT,
                                "连接能力定义重复: " + capability.uniqueKey()
                        );
                    }
                })
                .toList();
    }

    private NormalizedCapability normalizeCapability(ConnectionCapabilityItemRequest request) {
        String capabilityCode = normalizeUpperRequired(request.capabilityCode(), "capabilityCode");
        String capabilityValue = normalizeUpperRequired(request.capabilityValue(), "capabilityValue");
        String scope = normalizeScope(request.scope());
        String scopeValue = normalizeBlank(request.scopeValue());
        if (!"GLOBAL".equals(scope) && scopeValue == null) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "作用域 " + scope + " 必须指定 scopeValue");
        }
        if ("GLOBAL".equals(scope)) {
            scopeValue = null;
        }
        return new NormalizedCapability(
                capabilityCode,
                capabilityValue,
                normalizeBlank(request.capabilityDetailJson()),
                scope,
                scopeValue,
                normalizeBlank(request.remark())
        );
    }

    private String normalizeScope(String scope) {
        String normalized = normalizeBlank(scope);
        if (normalized == null) {
            return "GLOBAL";
        }
        String upper = normalized.toUpperCase();
        return switch (upper) {
            case "GLOBAL", "SCHEMA", "TABLE", "CUSTOM" -> upper;
            default -> throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "不支持的能力作用域: " + normalized);
        };
    }

    private String normalizeUpperRequired(String value, String fieldName) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, fieldName + " 不能为空");
        }
        return normalized.toUpperCase();
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ConnectionCapabilityResponse toResponse(DsConnectionRecord connection) {
        List<ConnectionCapabilityItemResponse> items = sourceCapabilityRepository.findEnabledByConnectionId(connection.getId()).stream()
                .map(record -> new ConnectionCapabilityItemResponse(
                        record.getCapabilityCode(),
                        record.getCapabilityValue(),
                        record.getCapabilityDetailJson(),
                        record.getScope(),
                        record.getScopeValue(),
                        record.getStatus(),
                        record.getRemark()
                ))
                .toList();
        return new ConnectionCapabilityResponse(
                connection.getId(),
                connection.getConnectionCode(),
                connection.getDbType(),
                items
        );
    }

    private String buildAuditDetail(ConnectionCapabilityResponse response) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("connectionId", response.connectionId());
        detail.put("connectionCode", response.connectionCode());
        detail.put("dbType", response.dbType());
        detail.put("capabilities", response.capabilities());
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "连接能力审计详情序列化失败", ex);
        }
    }

    private record NormalizedCapability(
            String capabilityCode,
            String capabilityValue,
            String capabilityDetailJson,
            String scope,
            String scopeValue,
            String remark
    ) {
        private String uniqueKey() {
            if (scopeValue == null || scopeValue.isBlank()) {
                return capabilityCode + "@" + scope;
            }
            return capabilityCode + "@" + scope + ":" + scopeValue;
        }
    }
}
