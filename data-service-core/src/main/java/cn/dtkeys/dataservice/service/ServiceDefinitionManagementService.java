package cn.dtkeys.dataservice.service;

import cn.dtkeys.dataservice.common.context.OperatorContext;
import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import cn.dtkeys.dataservice.common.exception.ServiceNotFoundException;
import cn.dtkeys.dataservice.service.model.DSDefinition;
import cn.dtkeys.dataservice.service.model.DSField;
import cn.dtkeys.dataservice.service.model.DSParam;
import cn.dtkeys.dataservice.service.model.DSServiceVersion;
import cn.dtkeys.dataservice.service.model.DSSource;
import cn.dtkeys.dataservice.service.model.DSSqlPlan;
import cn.dtkeys.dataservice.service.model.DSSqlText;
import cn.dtkeys.dataservice.service.model.DSSqlValidateLog;
import cn.dtkeys.dataservice.audit.AuditEvent;
import cn.dtkeys.dataservice.audit.AuditLogService;
import cn.dtkeys.dataservice.audit.AuditAction;
import cn.dtkeys.dataservice.query.executor.SqlReadOnlyValidator;
import cn.dtkeys.dataservice.repository.DSDefinitionRepository;
import cn.dtkeys.dataservice.repository.DSFieldRepository;
import cn.dtkeys.dataservice.repository.DSParamRepository;
import cn.dtkeys.dataservice.repository.DSServiceVersionRepository;
import cn.dtkeys.dataservice.repository.DSSourceRepository;
import cn.dtkeys.dataservice.repository.DSSqlPlanRepository;
import cn.dtkeys.dataservice.repository.DSSqlTextRepository;
import cn.dtkeys.dataservice.repository.DSSqlValidateLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据服务草稿与配置管理应用服务。
 */
@Service
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceDefinitionManagementService {

    private final DSDefinitionRepository dsDefinitionRepository;
    private final DSSourceRepository dsSourceRepository;
    private final DSParamRepository dsParamRepository;
    private final DSFieldRepository dsFieldRepository;
    private final DSServiceVersionRepository dsServiceVersionRepository;
    private final DSSqlTextRepository dsSqlTextRepository;
    private final DSSqlPlanRepository dsSqlPlanRepository;
    private final DSSqlValidateLogRepository dsSqlValidateLogRepository;
    private final SqlReadOnlyValidator sqlReadOnlyValidator;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public ServiceDefinitionManagementService(DSDefinitionRepository dsDefinitionRepository,
                                              DSSourceRepository dsSourceRepository,
                                              DSParamRepository dsParamRepository,
                                              DSFieldRepository dsFieldRepository,
                                              DSServiceVersionRepository dsServiceVersionRepository,
                                              DSSqlTextRepository dsSqlTextRepository,
                                              DSSqlPlanRepository dsSqlPlanRepository,
                                              DSSqlValidateLogRepository dsSqlValidateLogRepository,
                                              SqlReadOnlyValidator sqlReadOnlyValidator,
                                              AuditLogService auditLogService,
                                              ObjectMapper objectMapper) {
        this.dsDefinitionRepository = dsDefinitionRepository;
        this.dsSourceRepository = dsSourceRepository;
        this.dsParamRepository = dsParamRepository;
        this.dsFieldRepository = dsFieldRepository;
        this.dsServiceVersionRepository = dsServiceVersionRepository;
        this.dsSqlTextRepository = dsSqlTextRepository;
        this.dsSqlPlanRepository = dsSqlPlanRepository;
        this.dsSqlValidateLogRepository = dsSqlValidateLogRepository;
        this.sqlReadOnlyValidator = sqlReadOnlyValidator;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    public List<DSDefinition> listDefinitions() {
        return dsDefinitionRepository.findAll();
    }

    public ServiceDefinitionDetail getDefinition(Long id) {
        DSDefinition definition = dsDefinitionRepository.findById(id);
        if (definition == null) {
            throw new ServiceNotFoundException("未找到数据服务: " + id);
        }
        DSSqlText federatedSqlDraft = dsSqlTextRepository.findLatestDraftByServiceId(id);
        int metadataVersion = resolveMetadataVersion(definition, federatedSqlDraft);
        return new ServiceDefinitionDetail(
            definition,
            dsSourceRepository.findByServiceId(id),
            dsParamRepository.findByServiceId(id),
            dsFieldRepository.findByServiceId(id),
            federatedSqlDraft,
            dsSqlValidateLogRepository.findByServiceIdAndVersion(id, metadataVersion),
            dsSqlPlanRepository.findByServiceIdAndVersion(id, metadataVersion)
        );
    }

    public ServiceDefinitionDetail createDraft(DSDefinition definition,
                                               List<DSSource> sources,
                                               List<DSParam> params,
                                               List<DSField> fields) {
        normalizeDefinition(definition, sources);
        validateDefinition(definition, true);
        ensureServiceCodeNotExists(definition.getServiceCode(), null);
        String operator = OperatorContext.getOperator().orElse("SYSTEM");
        definition.setStatus("DRAFT");
        definition.setDeleted(false);
        definition.setCreatedBy(operator);
        definition.setUpdatedBy(operator);
        if (definition.getVersion() == null) {
            definition.setVersion(0);
        }
        dsDefinitionRepository.insert(definition);
        replaceChildren(definition.getId(), sources, params, fields, operator);
        auditLogService.record(AuditEvent.of(
            AuditAction.CREATE_SERVICE.name(),
            definition.getId(),
            null,
            "SERVICE",
            definition.getServiceCode(),
            operator,
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "创建服务草稿",
            Map.of("sourceCount", sources.size(), "paramCount", params.size(), "fieldCount", fields.size())
        ));
        return getDefinition(definition.getId());
    }

    public ServiceDefinitionDetail updateDraft(Long id,
                                               DSDefinition definition,
                                               List<DSSource> sources,
                                               List<DSParam> params,
                                               List<DSField> fields) {
        DSDefinition existing = dsDefinitionRepository.findById(id);
        if (existing == null) {
            throw new ServiceNotFoundException("未找到数据服务: " + id);
        }
        if (!"DRAFT".equalsIgnoreCase(existing.getStatus())) {
            throw new ParamInvalidException("仅允许编辑草稿态服务");
        }
        normalizeDefinition(definition, sources);
        validateDefinition(definition, false);
        ensureServiceCodeNotExists(definition.getServiceCode(), id);
        String operator = OperatorContext.getOperator().orElse("SYSTEM");
        definition.setId(id);
        definition.setStatus(existing.getStatus());
        definition.setDeleted(existing.getDeleted());
        definition.setCreatedBy(existing.getCreatedBy());
        definition.setUpdatedBy(operator);
        definition.setVersion(existing.getVersion());
        definition.setPublishedAt(existing.getPublishedAt());
        definition.setPublishedBy(existing.getPublishedBy());
        dsDefinitionRepository.update(definition);
        replaceChildren(id, sources, params, fields, operator);
        auditLogService.record(AuditEvent.of(
            AuditAction.UPDATE_SERVICE.name(),
            id,
            null,
            "SERVICE",
            definition.getServiceCode(),
            operator,
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "更新服务草稿",
            Map.of("sourceCount", sources.size(), "paramCount", params.size(), "fieldCount", fields.size())
        ));
        return getDefinition(id);
    }

    public ServiceDefinitionDetail publish(Long id) {
        DSDefinition existing = dsDefinitionRepository.findById(id);
        if (existing == null) {
            throw new ServiceNotFoundException("未找到数据服务: " + id);
        }
        ServiceDefinitionDetail detail = getDefinition(id);
        validatePublish(detail);
        String operator = OperatorContext.getOperator().orElse("SYSTEM");
        int publishedVersion = resolvePublishedVersion(existing, detail);
        publishFederatedSqlSnapshot(detail, publishedVersion, operator);
        saveVersionSnapshot(detail, publishedVersion, "PUBLISHED", operator);

        existing.setStatus("PUBLISHED");
        existing.setPlanStatus("PUBLISHED");
        existing.setVersion(publishedVersion);
        existing.setCurrentSqlVersion(publishedVersion);
        existing.setPublishedAt(java.time.LocalDateTime.now());
        existing.setPublishedBy(operator);
        existing.setUpdatedBy(operator);
        dsDefinitionRepository.update(existing);

        auditLogService.record(AuditEvent.of(
            AuditAction.PUBLISH_SERVICE.name(),
            id,
            null,
            "SERVICE",
            existing.getServiceCode(),
            operator,
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "发布服务版本 v" + publishedVersion,
            Map.of(
                "versionInfo", Map.of("version", publishedVersion, "status", "PUBLISHED"),
                "validation", Map.of("passed", true, "sourceCount", detail.sources().size(),
                    "paramCount", detail.params().size(), "fieldCount", detail.fields().size()),
                "federatedSql", buildFederatedPublishTrace(detail, publishedVersion)
            )
        ));
        return getDefinition(id);
    }

    public ServiceDefinitionDetail updateStatus(Long id, String status) {
        DSDefinition existing = dsDefinitionRepository.findById(id);
        if (existing == null) {
            throw new ServiceNotFoundException("未找到数据服务: " + id);
        }
        String normalizedStatus = normalizeStatus(status);
        if ("DRAFT".equals(normalizedStatus)) {
            throw new ParamInvalidException("仅允许停用已发布服务");
        }
        if ("DISABLED".equals(normalizedStatus) && !"PUBLISHED".equals(existing.getStatus())) {
            throw new ParamInvalidException("仅允许停用已发布服务");
        }
        existing.setStatus(normalizedStatus);
        existing.setUpdatedBy(OperatorContext.getOperator().orElse("SYSTEM"));
        dsDefinitionRepository.update(existing);
        auditLogService.record(AuditEvent.of(
            AuditAction.DISABLE_SERVICE.name(),
            id,
            null,
            "SERVICE",
            existing.getServiceCode(),
            OperatorContext.getOperator().orElse("SYSTEM"),
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "停用服务版本 v" + existing.getVersion(),
            Map.of("versionInfo", Map.of("version", existing.getVersion(), "status", normalizedStatus))
        ));
        return getDefinition(id);
    }

    public List<DSServiceVersion> listVersions(Long id) {
        DSDefinition existing = dsDefinitionRepository.findById(id);
        if (existing == null) {
            throw new ServiceNotFoundException("未找到数据服务: " + id);
        }
        return dsServiceVersionRepository.findByServiceId(id);
    }

    public void deleteDefinition(Long id) {
        DSDefinition existing = dsDefinitionRepository.findById(id);
        if (existing == null) {
            throw new ServiceNotFoundException("未找到数据服务: " + id);
        }
        if ("PUBLISHED".equalsIgnoreCase(existing.getStatus())) {
            throw new ParamInvalidException("已发布服务不允许直接删除，请先停用");
        }
        String operator = OperatorContext.getOperator().orElse("SYSTEM");
        dsDefinitionRepository.softDeleteById(id, operator);
        auditLogService.record(AuditEvent.of(
            AuditAction.DELETE_SERVICE.name(),
            id,
            null,
            "SERVICE",
            existing.getServiceCode(),
            operator,
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "删除服务",
            Map.of("status", existing.getStatus(), "version", existing.getVersion())
        ));
    }

    private void replaceChildren(Long serviceId,
                                 List<DSSource> sources,
                                 List<DSParam> params,
                                 List<DSField> fields,
                                 String operator) {
        dsSourceRepository.deleteByServiceId(serviceId);
        dsParamRepository.deleteByServiceId(serviceId);
        dsFieldRepository.deleteByServiceId(serviceId);
        for (DSSource source : sources) {
            validateSource(source);
            source.setServiceId(serviceId);
            source.setDeleted(false);
            source.setCreatedBy(operator);
            source.setUpdatedBy(operator);
            dsSourceRepository.insert(source);
        }
        for (DSParam param : params) {
            validateParam(param);
            param.setServiceId(serviceId);
            param.setDeleted(false);
            param.setCreatedBy(operator);
            param.setUpdatedBy(operator);
            dsParamRepository.insert(param);
        }
        for (DSField field : fields) {
            validateField(field);
            field.setServiceId(serviceId);
            field.setDeleted(false);
            field.setCreatedBy(operator);
            field.setUpdatedBy(operator);
            dsFieldRepository.insert(field);
        }
    }

    private void ensureServiceCodeNotExists(String serviceCode, Long currentId) {
        DSDefinition existing = dsDefinitionRepository.findByServiceCode(serviceCode);
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new ParamInvalidException("serviceCode 已存在: " + serviceCode);
        }
    }

    private void validateDefinition(DSDefinition definition, boolean requireCode) {
        if (definition == null) {
            throw new ParamInvalidException("服务定义不能为空");
        }
        if (requireCode && isBlank(definition.getServiceCode())) {
            throw new ParamInvalidException("serviceCode 不能为空");
        }
        if (isBlank(definition.getServiceName())) {
            throw new ParamInvalidException("serviceName 不能为空");
        }
        if (isBlank(definition.getServiceType())) {
            throw new ParamInvalidException("serviceType 不能为空");
        }
        if (isBlank(definition.getSqlType())) {
            throw new ParamInvalidException("sqlType 不能为空");
        }
        if (!isBlank(definition.getSqlTemplate())) {
            sqlReadOnlyValidator.validate(definition.getSqlTemplate());
        }
    }

    private void normalizeDefinition(DSDefinition definition, List<DSSource> sources) {
        definition.setServiceType(resolveServiceType(definition));
        definition.setExecutionMode(resolveExecutionMode(definition, sources));
        definition.setPlanStatus(resolvePlanStatus(definition));
        definition.setMaxBatchSize(null);
        definition.setMaxResultRows(null);
        definition.setQueryTimeoutSeconds(null);
        definition.setFederatedQueryTimeoutSeconds(null);
    }

    private String resolveServiceType(DSDefinition definition) {
        if ("FEDERATED_SQL".equalsIgnoreCase(definition.getSqlType())
            || "FEDERATED_QUERY".equalsIgnoreCase(definition.getServiceType())) {
            return "FEDERATED_QUERY";
        }
        return "SIMPLE_QUERY";
    }

    private String resolveExecutionMode(DSDefinition definition, List<DSSource> sources) {
        if ("FEDERATED_SQL".equalsIgnoreCase(definition.getSqlType())) {
            return "REMOTE_PLUS_LOCAL";
        }
        if (sources != null && sources.size() > 1) {
            return "REMOTE_PLUS_LOCAL";
        }
        String sqlTemplate = definition.getSqlTemplate();
        if (!isBlank(sqlTemplate) && sqlTemplate.toUpperCase().contains(" JOIN ")) {
            return "REMOTE_PLUS_LOCAL";
        }
        return "REMOTE_ONLY";
    }

    private String resolvePlanStatus(DSDefinition definition) {
        if ("PUBLISHED".equalsIgnoreCase(definition.getStatus())) {
            return "PUBLISHED";
        }
        return "UNPLANNED";
    }

    private void validatePublish(ServiceDefinitionDetail detail) {
        if (detail.sources().isEmpty()) {
            throw new ServiceConfigInvalidException("发布失败: 未配置来源");
        }
        if (detail.params().isEmpty()) {
            throw new ServiceConfigInvalidException("发布失败: 未配置参数");
        }
        if (detail.fields().isEmpty()) {
            throw new ServiceConfigInvalidException("发布失败: 未配置返回字段");
        }
        if (isBlank(detail.definition().getSqlTemplate())) {
            throw new ServiceConfigInvalidException("发布失败: 未配置 SQL");
        }
        if ("FEDERATED_QUERY".equalsIgnoreCase(detail.definition().getServiceType())
            && detail.federatedSqlDraft() == null) {
            throw new ServiceConfigInvalidException("发布失败: 未生成联邦 SQL 草稿");
        }
        for (DSSource source : detail.sources()) {
            validateSource(source);
        }
        for (DSParam param : detail.params()) {
            validateParam(param);
        }
        for (DSField field : detail.fields()) {
            validateField(field);
        }
    }

    private void validateSource(DSSource source) {
        if (isBlank(source.getSourceAlias())) {
            throw new ParamInvalidException("sourceAlias 不能为空");
        }
        if (source.getConnectionId() == null) {
            throw new ParamInvalidException("source.connectionId 不能为空");
        }
        if (isBlank(source.getSourceType())) {
            throw new ParamInvalidException("sourceType 不能为空");
        }
        if (isBlank(source.getSourceValue())) {
            throw new ParamInvalidException("sourceValue 不能为空");
        }
        if (isBlank(source.getStatus())) {
            source.setStatus("ENABLED");
        }
    }

    private void validateParam(DSParam param) {
        if (isBlank(param.getParamName())) {
            throw new ParamInvalidException("paramName 不能为空");
        }
        if (isBlank(param.getDisplayName())) {
            throw new ParamInvalidException("param.displayName 不能为空");
        }
        if (isBlank(param.getParamType())) {
            throw new ParamInvalidException("paramType 不能为空");
        }
        if (isBlank(param.getSqlPlaceholder())) {
            throw new ParamInvalidException("sqlPlaceholder 不能为空");
        }
        if (param.getSortOrder() == null) {
            throw new ParamInvalidException("param.sortOrder 不能为空");
        }
        if (param.getRequired() == null) {
            param.setRequired(Boolean.TRUE);
        }
    }

    private void validateField(DSField field) {
        if (isBlank(field.getFieldName())) {
            throw new ParamInvalidException("fieldName 不能为空");
        }
        if (isBlank(field.getDisplayName())) {
            throw new ParamInvalidException("field.displayName 不能为空");
        }
        if (isBlank(field.getFieldType())) {
            throw new ParamInvalidException("fieldType 不能为空");
        }
        if (isBlank(field.getSourceColumn())) {
            throw new ParamInvalidException("sourceColumn 不能为空");
        }
        if (field.getSortOrder() == null) {
            throw new ParamInvalidException("field.sortOrder 不能为空");
        }
        if (field.getPrimaryKey() == null) {
            field.setPrimaryKey(Boolean.FALSE);
        }
        if (field.getJoinKey() == null) {
            field.setJoinKey(Boolean.FALSE);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void saveVersionSnapshot(ServiceDefinitionDetail detail,
                                     int version,
                                     String status,
                                     String operator) {
        DSServiceVersion serviceVersion = new DSServiceVersion();
        serviceVersion.setServiceId(detail.definition().getId());
        serviceVersion.setVersion(version);
        serviceVersion.setStatus(status);
        serviceVersion.setServiceDefinitionJson(toJson(detail.definition()));
        serviceVersion.setSourceDefinitionJson(toJson(detail.sources()));
        serviceVersion.setParamDefinitionJson(toJson(detail.params()));
        serviceVersion.setFieldDefinitionJson(toJson(detail.fields()));
        serviceVersion.setSqlDefinitionJson(toJson(buildSqlDefinitionSnapshot(detail, version)));
        serviceVersion.setCreatedBy(operator);
        dsServiceVersionRepository.insert(serviceVersion);
    }

    private Map<String, Object> buildSqlDefinitionSnapshot(ServiceDefinitionDetail detail, int version) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sqlTemplate", detail.definition().getSqlTemplate());
        snapshot.put("sqlType", detail.definition().getSqlType());
        snapshot.put("executionMode", detail.definition().getExecutionMode());
        snapshot.put("planStatus", detail.definition().getPlanStatus());
        if ("FEDERATED_QUERY".equalsIgnoreCase(detail.definition().getServiceType()) && detail.federatedSqlDraft() != null) {
            snapshot.put("federatedSqlVersion", version);
            snapshot.put("federatedSqlStatus", "PUBLISHED");
            snapshot.put("federatedSqlText", detail.federatedSqlDraft().getSqlText());
            snapshot.put("federatedSqlComment", detail.federatedSqlDraft().getSqlComment());
            snapshot.put("validateLogCount", detail.validateLogs().size());
            snapshot.put("planArtifactCount", detail.plans().size());
        }
        return snapshot;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ServiceConfigInvalidException("服务版本快照序列化失败");
        }
    }

    private String normalizeStatus(String status) {
        if (isBlank(status)) {
            throw new ParamInvalidException("status 不能为空");
        }
        String normalized = status.trim().toUpperCase();
        if (!"DRAFT".equals(normalized) && !"PUBLISHED".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new ParamInvalidException("非法状态值: " + status);
        }
        return normalized;
    }

    private int resolveMetadataVersion(DSDefinition definition, DSSqlText draftSql) {
        if (draftSql != null && draftSql.getVersion() != null) {
            return draftSql.getVersion();
        }
        return definition.getCurrentSqlVersion() == null ? 0 : definition.getCurrentSqlVersion();
    }

    private int resolvePublishedVersion(DSDefinition existing, ServiceDefinitionDetail detail) {
        int nextServiceVersion = existing.getVersion() == null ? 1 : existing.getVersion() + 1;
        if (!"FEDERATED_QUERY".equalsIgnoreCase(existing.getServiceType())) {
            return nextServiceVersion;
        }
        DSSqlText draftSql = detail.federatedSqlDraft();
        if (draftSql == null || draftSql.getVersion() == null) {
            throw new ServiceConfigInvalidException("发布失败: 未找到联邦 SQL 草稿版本");
        }
        if (draftSql.getVersion() != nextServiceVersion) {
            throw new ServiceConfigInvalidException("发布失败: 联邦 SQL 版本与服务版本不一致");
        }
        return draftSql.getVersion();
    }

    private void publishFederatedSqlSnapshot(ServiceDefinitionDetail detail, int version, String operator) {
        if (!"FEDERATED_QUERY".equalsIgnoreCase(detail.definition().getServiceType())) {
            return;
        }
        dsSqlTextRepository.clearCurrentVersion(detail.definition().getId(), operator);
        int updatedRows = dsSqlTextRepository.markPublished(detail.definition().getId(), version, operator);
        if (updatedRows == 0) {
            throw new ServiceConfigInvalidException("发布失败: 联邦 SQL 草稿不存在");
        }
    }

    private Map<String, Object> buildFederatedPublishTrace(ServiceDefinitionDetail detail, int version) {
        if (!"FEDERATED_QUERY".equalsIgnoreCase(detail.definition().getServiceType()) || detail.federatedSqlDraft() == null) {
            return Map.of();
        }
        return Map.of(
            "sqlVersion", version,
            "status", "PUBLISHED",
            "validateLogCount", detail.validateLogs().size(),
            "planArtifactCount", detail.plans().size()
        );
    }

    public record ServiceDefinitionDetail(DSDefinition definition,
                                          List<DSSource> sources,
                                          List<DSParam> params,
                                          List<DSField> fields,
                                          DSSqlText federatedSqlDraft,
                                          List<DSSqlValidateLog> validateLogs,
                                          List<DSSqlPlan> plans) {
    }
}
