package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.ResourceConflictException;
import cn.dtkeys.dataservice.core.error.ResourceNotFoundException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsConnectionAdminRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsConnectionRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsPublishedServiceLookupRepository;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionCreateRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionPayload;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionStatusUpdateRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionTestPayload;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionUpdateRequest;
import cn.dtkeys.dataservice.core.meta.web.response.ConnectionDetailResponse;
import cn.dtkeys.dataservice.core.meta.web.response.ConnectionTestResponse;
import cn.dtkeys.dataservice.core.meta.web.support.OperatorContext;
import cn.dtkeys.dataservice.core.security.CredentialCodec;
import cn.dtkeys.dataservice.core.security.SensitiveDataMasker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConnectionService {

    private final DsConnectionRepository connectionRepository;
    private final DsConnectionAdminRepository connectionAdminRepository;
    private final DsPublishedServiceLookupRepository publishedServiceLookupRepository;
    private final ConnectionTestExecutor connectionTestExecutor;
    private final CredentialCodec credentialCodec;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final AuditService auditService;

    public ConnectionService(DsConnectionRepository connectionRepository,
                             DsConnectionAdminRepository connectionAdminRepository,
                             DsPublishedServiceLookupRepository publishedServiceLookupRepository,
                             ConnectionTestExecutor connectionTestExecutor,
                             CredentialCodec credentialCodec,
                             SensitiveDataMasker sensitiveDataMasker,
                             AuditService auditService) {
        this.connectionRepository = connectionRepository;
        this.connectionAdminRepository = connectionAdminRepository;
        this.publishedServiceLookupRepository = publishedServiceLookupRepository;
        this.connectionTestExecutor = connectionTestExecutor;
        this.credentialCodec = credentialCodec;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.auditService = auditService;
    }

    /**
     * 新增连接并写入审计。
     */
    @Transactional
    public ConnectionDetailResponse create(ConnectionCreateRequest request, OperatorContext operatorContext, String traceId) {
        connectionTestExecutor.test(toPayload(request));
        DsConnectionRecord record = new DsConnectionRecord();
        fillRecord(record, request, operatorContext.operator());
        record.setStatus("ENABLED");
        try {
            connectionRepository.insert(record);
        } catch (DuplicateKeyException ex) {
            throw new ResourceConflictException("连接编码已存在: " + request.connectionCode());
        }
        auditService.recordConnectionEvent(
                record.getId(), "CREATE_CONNECTION", request.connectionCode(), "SUCCESS", "新增连接成功",
                "{\"connectionCode\":\"" + request.connectionCode() + "\"}", operatorContext, traceId
        );
        return toDetailResponse(record);
    }

    /**
     * 编辑连接并写入审计。
     */
    @Transactional
    public ConnectionDetailResponse update(Long id, ConnectionUpdateRequest request, OperatorContext operatorContext, String traceId) {
        DsConnectionRecord existing = requireConnection(id);
        connectionTestExecutor.test(toPayload(request));
        fillRecord(existing, request, operatorContext.operator());
        existing.setId(id);
        connectionAdminRepository.update(existing);
        auditService.recordConnectionEvent(
                id, "UPDATE_CONNECTION", existing.getConnectionCode(), "SUCCESS", "编辑连接成功",
                "{\"connectionCode\":\"" + existing.getConnectionCode() + "\"}", operatorContext, traceId
        );
        return toDetailResponse(requireConnection(id));
    }

    /**
     * 更新连接状态并校验发布引用。
     */
    @Transactional
    public ConnectionDetailResponse updateStatus(Long id,
                                                 ConnectionStatusUpdateRequest request,
                                                 OperatorContext operatorContext,
                                                 String traceId) {
        DsConnectionRecord existing = requireConnection(id);
        if ("DISABLED".equals(request.status())) {
            long referenced = publishedServiceLookupRepository.countPublishedServicesUsingConnection(existing.getConnectionCode());
            if (referenced > 0) {
                throw new ResourceConflictException(ErrorCode.DATASOURCE_DISABLED_IN_USE, "连接已被已发布服务引用，不能停用");
            }
        }
        connectionRepository.updateStatus(id, request.status(), operatorContext.operator());
        auditService.recordConnectionEvent(
                id, "UPDATE_CONNECTION_STATUS", existing.getConnectionCode(), "SUCCESS", "更新连接状态成功",
                "{\"status\":\"" + request.status() + "\"}", operatorContext, traceId
        );
        return toDetailResponse(requireConnection(id));
    }

    /**
     * 测试现有连接。
     */
    public ConnectionTestResponse testExisting(Long id, OperatorContext operatorContext, String traceId) {
        DsConnectionRecord existing = requireConnection(id);
        ConnectionPayload payload = new ConnectionTestPayload(
                existing.getConnectionName(),
                existing.getDbType(),
                existing.getHost(),
                existing.getPort(),
                existing.getUsername(),
                credentialCodec.decrypt(existing.getPasswordCiphertext()),
                extractDatabaseName(existing.getConnectionConfigJson()),
                existing.getRemark()
        );
        connectionTestExecutor.test(payload);
        auditService.recordConnectionEvent(
                id, "TEST_CONNECTION", existing.getConnectionCode(), "SUCCESS", "测试连接成功",
                "{\"connectionCode\":\"" + existing.getConnectionCode() + "\"}", operatorContext, traceId
        );
        return new ConnectionTestResponse(true, "连接成功");
    }

    /**
     * 查询连接详情。
     */
    public ConnectionDetailResponse get(Long id) {
        return toDetailResponse(requireConnection(id));
    }

    /**
     * 查询连接列表。
     */
    public List<ConnectionDetailResponse> list(String status) {
        return connectionAdminRepository.findAll(status).stream().map(this::toDetailResponse).toList();
    }

    private DsConnectionRecord requireConnection(Long id) {
        DsConnectionRecord record = connectionAdminRepository.findById(id);
        if (record == null) {
            throw new ResourceNotFoundException("连接不存在: " + id);
        }
        return record;
    }

    private void fillRecord(DsConnectionRecord record, ConnectionCreateRequest request, String operator) {
        record.setConnectionCode(request.connectionCode());
        fillRecord(record, (ConnectionPayload) request, operator);
    }

    private void fillRecord(DsConnectionRecord record, ConnectionUpdateRequest request, String operator) {
        fillRecord(record, (ConnectionPayload) request, operator);
    }

    private void fillRecord(DsConnectionRecord record, ConnectionPayload request, String operator) {
        record.setConnectionName(request.connectionName());
        record.setDbType(request.dbType());
        record.setHost(request.host());
        record.setPort(request.port());
        record.setUsername(request.username());
        record.setPasswordCiphertext(credentialCodec.encrypt(request.password()));
        record.setRemark(request.remark());
        record.setConnectionConfigJson("{\"databaseName\":\"" + request.databaseName() + "\"}");
        record.setDeleted(false);
        if (record.getCreatedBy() == null) {
            record.setCreatedBy(operator);
        }
        record.setUpdatedBy(operator);
    }

    private ConnectionPayload toPayload(ConnectionCreateRequest request) {
        return request;
    }

    private ConnectionPayload toPayload(ConnectionUpdateRequest request) {
        return request;
    }

    private String extractDatabaseName(String connectionConfigJson) {
        if (connectionConfigJson == null || connectionConfigJson.isBlank()) {
            return "";
        }
        String marker = "\"databaseName\":\"";
        int start = connectionConfigJson.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        int valueEnd = connectionConfigJson.indexOf('"', valueStart);
        return valueEnd > valueStart ? connectionConfigJson.substring(valueStart, valueEnd) : "";
    }

    private ConnectionDetailResponse toDetailResponse(DsConnectionRecord record) {
        return new ConnectionDetailResponse(
                record.getId(),
                record.getConnectionCode(),
                record.getConnectionName(),
                record.getDbType(),
                sensitiveDataMasker.maskHost(record.getHost()),
                record.getPort(),
                sensitiveDataMasker.maskUsername(record.getUsername()),
                sensitiveDataMasker.maskPassword(),
                record.getStatus(),
                record.getRemark(),
                record.getConnectionConfigJson(),
                record.getCreatedAt(),
                record.getCreatedBy(),
                record.getUpdatedAt(),
                record.getUpdatedBy()
        );
    }
}
