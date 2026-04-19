package cn.dtkeys.dataservice.federation;

import cn.dtkeys.dataservice.common.context.OperatorContext;
import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import cn.dtkeys.dataservice.common.exception.ServiceNotFoundException;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.service.model.DSDefinition;
import cn.dtkeys.dataservice.datasource.model.DSSourceCapability;
import cn.dtkeys.dataservice.service.model.DSSqlPlan;
import cn.dtkeys.dataservice.service.model.DSSqlText;
import cn.dtkeys.dataservice.service.model.DSSqlValidateLog;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeDefinition;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeSource;
import cn.dtkeys.dataservice.federation.diagnostics.FederatedPlanDiagnosticsService;
import cn.dtkeys.dataservice.federation.executor.FederatedRuntimeQueryExecutor;
import cn.dtkeys.dataservice.federation.model.FederatedDiagnostics;
import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.ValidationResult;
import cn.dtkeys.dataservice.federation.optimizer.FederatedSqlOptimizer;
import cn.dtkeys.dataservice.federation.parser.FederatedSqlParser;
import cn.dtkeys.dataservice.federation.planner.FederatedSqlPlanner;
import cn.dtkeys.dataservice.federation.validator.FederatedSqlValidator;
import cn.dtkeys.dataservice.audit.AuditEvent;
import cn.dtkeys.dataservice.audit.AuditLogService;
import cn.dtkeys.dataservice.audit.AuditAction;
import cn.dtkeys.dataservice.repository.DSConnectionRepository;
import cn.dtkeys.dataservice.repository.DSDefinitionRepository;
import cn.dtkeys.dataservice.repository.DSSourceRepository;
import cn.dtkeys.dataservice.repository.DSSourceCapabilityRepository;
import cn.dtkeys.dataservice.repository.DSSqlPlanRepository;
import cn.dtkeys.dataservice.repository.DSSqlTextRepository;
import cn.dtkeys.dataservice.repository.DSSqlValidateLogRepository;
import cn.dtkeys.dataservice.repository.DSCachePolicyRepository;
import cn.dtkeys.dataservice.repository.DSCatalogRepository;
import cn.dtkeys.dataservice.repository.DSFieldRepository;
import cn.dtkeys.dataservice.repository.DSParamRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
public class FederatedMetadataManagementService {

    private final DSDefinitionRepository dsDefinitionRepository;
    private final DSConnectionRepository dsConnectionRepository;
    private final DSSourceRepository dsSourceRepository;
    private final DSSourceCapabilityRepository dsSourceCapabilityRepository;
    private final DSSqlTextRepository dsSqlTextRepository;
    private final DSSqlPlanRepository dsSqlPlanRepository;
    private final DSSqlValidateLogRepository dsSqlValidateLogRepository;
    private final DSCachePolicyRepository dsCachePolicyRepository;
    private final DSCatalogRepository dsCatalogRepository;
    private final DSParamRepository dsParamRepository;
    private final DSFieldRepository dsFieldRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final FederatedRuntimeQueryExecutor federatedRuntimeQueryExecutor;
    private final FederatedSqlParser federatedSqlParser;
    private final FederatedSqlValidator federatedSqlValidator;
    private final FederatedSqlPlanner federatedSqlPlanner;
    private final FederatedSqlOptimizer federatedSqlOptimizer;
    private final FederatedPlanDiagnosticsService federatedPlanDiagnosticsService;

    public FederatedMetadataManagementService(DSDefinitionRepository dsDefinitionRepository,
                                              DSConnectionRepository dsConnectionRepository,
                                              DSSourceRepository dsSourceRepository,
                                              DSSourceCapabilityRepository dsSourceCapabilityRepository,
                                              DSSqlTextRepository dsSqlTextRepository,
                                              DSSqlPlanRepository dsSqlPlanRepository,
                                              DSSqlValidateLogRepository dsSqlValidateLogRepository,
                                              DSCachePolicyRepository dsCachePolicyRepository,
                                              DSCatalogRepository dsCatalogRepository,
                                              DSParamRepository dsParamRepository,
                                              DSFieldRepository dsFieldRepository,
                                              AuditLogService auditLogService,
                                              ObjectMapper objectMapper,
                                              FederatedRuntimeQueryExecutor federatedRuntimeQueryExecutor) {
        this.dsDefinitionRepository = dsDefinitionRepository;
        this.dsConnectionRepository = dsConnectionRepository;
        this.dsSourceRepository = dsSourceRepository;
        this.dsSourceCapabilityRepository = dsSourceCapabilityRepository;
        this.dsSqlTextRepository = dsSqlTextRepository;
        this.dsSqlPlanRepository = dsSqlPlanRepository;
        this.dsSqlValidateLogRepository = dsSqlValidateLogRepository;
        this.dsCachePolicyRepository = dsCachePolicyRepository;
        this.dsCatalogRepository = dsCatalogRepository;
        this.dsParamRepository = dsParamRepository;
        this.dsFieldRepository = dsFieldRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.federatedRuntimeQueryExecutor = federatedRuntimeQueryExecutor;
        this.federatedSqlParser = new FederatedSqlParser();
        this.federatedSqlValidator = new FederatedSqlValidator();
        this.federatedSqlPlanner = new FederatedSqlPlanner();
        this.federatedSqlOptimizer = new FederatedSqlOptimizer();
        this.federatedPlanDiagnosticsService = new FederatedPlanDiagnosticsService();
    }

    public FederatedServiceMetadata getFederatedMetadata(Long serviceId) {
        DSDefinition definition = getDefinition(serviceId);
        DSSqlText draftSql = dsSqlTextRepository.findLatestDraftByServiceId(serviceId);
        int draftVersion = resolveDraftVersion(definition, draftSql);
        return new FederatedServiceMetadata(
            definition,
            draftSql,
            dsSqlValidateLogRepository.findByServiceIdAndVersion(serviceId, draftVersion),
            dsSqlPlanRepository.findByServiceIdAndVersion(serviceId, draftVersion)
        );
    }

    public FederatedServiceMetadata saveFederatedSqlDraft(Long serviceId,
                                                          String sqlText,
                                                          String sqlComment) {
        DSDefinition definition = getDefinition(serviceId);
        validateFederatedService(definition);
        if (sqlText == null || sqlText.isBlank()) {
            throw new ParamInvalidException("federatedSqlText 不能为空");
        }

        String operator = OperatorContext.getOperator().orElse("SYSTEM");
        int nextVersion = resolveNextDraftVersion(definition);
        String traceId = UUID.randomUUID().toString();

        FederatedParsedQuery parsedQuery = parse(sqlText);
        ValidationResult validationResult = validateRecognizedSources(serviceId, federatedSqlValidator.validate(parsedQuery));
        if (!validationResult.valid()) {
            persistValidationLogs(serviceId, nextVersion, validationResult, traceId, operator);
            throw new ServiceConfigInvalidException("联邦 SQL 校验失败: " + String.join("; ", validationResult.errors()));
        }

        FederatedPlan planned;
        FederatedPlan optimized;
        try {
            planned = federatedSqlPlanner.plan(parsedQuery);
            optimized = federatedSqlOptimizer.optimize(planned);
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
            throw new ServiceConfigInvalidException("联邦 SQL 规划失败: " + exception.getMessage());
        }
        FederatedDiagnostics diagnostics = federatedPlanDiagnosticsService.buildDiagnostics(optimized);

        dsSqlTextRepository.deleteDraftByServiceId(serviceId);
        dsSqlValidateLogRepository.deleteByServiceIdAndVersion(serviceId, nextVersion);
        dsSqlPlanRepository.deleteByServiceIdAndVersion(serviceId, nextVersion);

        DSSqlText sqlDraft = new DSSqlText();
        sqlDraft.setServiceId(serviceId);
        sqlDraft.setVersion(nextVersion);
        sqlDraft.setSqlText(sqlText.trim());
        sqlDraft.setSqlComment(sqlComment);
        sqlDraft.setStatus("DRAFT");
        sqlDraft.setCurrent(false);
        sqlDraft.setCreatedBy(operator);
        sqlDraft.setUpdatedBy(operator);
        dsSqlTextRepository.insert(sqlDraft);

        persistValidationLogs(serviceId, nextVersion, validationResult, traceId, operator);
        persistPlanArtifacts(serviceId, nextVersion, optimized, diagnostics, operator);

        definition.setSqlType("FEDERATED_SQL");
        definition.setExecutionMode(resolveExecutionMode(parsedQuery));
        definition.setPlanStatus("PLANNED");
        definition.setSqlTemplate(sqlText.trim());
        definition.setUpdatedBy(operator);
        dsDefinitionRepository.update(definition);

        auditLogService.record(AuditEvent.of(
            AuditAction.SAVE_FEDERATED_SQL_DRAFT.name(),
            serviceId,
            null,
            "SERVICE",
            definition.getServiceCode(),
            operator,
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "保存联邦 SQL 草稿",
            Map.of(
                "draftVersion", nextVersion,
                "recognizedSources", validationResult.recognizedSources(),
                "datasourceScope", optimized.datasourceScope(),
                "planStatus", "PLANNED"
            )
        ));

        return getFederatedMetadata(serviceId);
    }

    public FederatedPreviewResult previewFederatedSql(Long serviceId,
                                                      String sqlText,
                                                      Map<String, Object> params) {
        DSDefinition definition = getDefinition(serviceId);
        validateFederatedService(definition);
        if (sqlText == null || sqlText.isBlank()) {
            throw new ParamInvalidException("federatedSqlText 不能为空");
        }

        FederatedParsedQuery parsedQuery = parse(sqlText);
        ValidationResult validationResult = validateRecognizedSources(serviceId, federatedSqlValidator.validate(parsedQuery));
        if (!validationResult.valid()) {
            throw new ServiceConfigInvalidException("联邦 SQL 校验失败: " + String.join("; ", validationResult.errors()));
        }

        FederatedPlan optimizedPlan;
        try {
            optimizedPlan = federatedSqlOptimizer.optimize(federatedSqlPlanner.plan(parsedQuery));
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
            throw new ServiceConfigInvalidException("联邦 SQL 规划失败: " + exception.getMessage());
        }

        DataServiceRuntimeDefinition runtimeDefinition = buildPreviewRuntimeDefinition(definition, sqlText.trim());
        FederatedRuntimeQueryExecutor.FederatedRuntimeExecutionResult executionResult = federatedRuntimeQueryExecutor.execute(
            runtimeDefinition,
            parsedQuery,
            optimizedPlan,
            params == null ? Map.of() : params,
            resolvePreviewTimeout(definition),
            resolvePreviewMaxRows(definition)
        );

        String operator = OperatorContext.getOperator().orElse("SYSTEM");
        auditLogService.record(AuditEvent.of(
            AuditAction.PREVIEW_FEDERATED_SQL.name(),
            serviceId,
            null,
            "SERVICE",
            definition.getServiceCode(),
            operator,
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "预览执行联邦 SQL",
            Map.of(
                "recognizedSources", validationResult.recognizedSources(),
                "rowCount", executionResult.rows().size(),
                "federatedExecution", executionResult.summary()
            )
        ));
        return new FederatedPreviewResult(executionResult.rows(), executionResult.summary());
    }

    public List<DSSourceCapability> listCapabilities(Long connectionId) {
        getConnection(connectionId);
        return dsSourceCapabilityRepository.findByConnectionId(connectionId);
    }

    public List<DSSourceCapability> replaceCapabilities(Long connectionId, List<DSSourceCapability> capabilities) {
        DSConnection connection = getConnection(connectionId);
        String operator = OperatorContext.getOperator().orElse("SYSTEM");
        dsSourceCapabilityRepository.deleteByConnectionId(connectionId);
        for (DSSourceCapability capability : capabilities) {
            validateCapability(capability, connection);
            capability.setConnectionId(connectionId);
            dsSourceCapabilityRepository.insert(capability);
        }
        auditLogService.record(AuditEvent.of(
            AuditAction.UPDATE_SOURCE_CAPABILITY.name(),
            null,
            connectionId,
            "CONNECTION",
            connection.getConnectionCode(),
            operator,
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "更新数据源能力矩阵",
            Map.of("capabilityCount", capabilities.size())
        ));
        return dsSourceCapabilityRepository.findByConnectionId(connectionId);
    }

    private DSDefinition getDefinition(Long serviceId) {
        DSDefinition definition = dsDefinitionRepository.findById(serviceId);
        if (definition == null) {
            throw new ServiceNotFoundException("未找到数据服务: " + serviceId);
        }
        return definition;
    }

    private DSConnection getConnection(Long connectionId) {
        DSConnection connection = dsConnectionRepository.findById(connectionId);
        if (connection == null) {
            throw new ServiceNotFoundException("未找到连接: " + connectionId);
        }
        return connection;
    }

    private void validateFederatedService(DSDefinition definition) {
        if (!"FEDERATED_QUERY".equalsIgnoreCase(definition.getServiceType())) {
            throw new ParamInvalidException("仅联邦查询服务支持维护联邦 SQL");
        }
    }

    private DataServiceRuntimeDefinition buildPreviewRuntimeDefinition(DSDefinition definition, String sqlText) {
        DSDefinition previewDefinition = new DSDefinition();
        previewDefinition.setId(definition.getId());
        previewDefinition.setServiceCode(definition.getServiceCode());
        previewDefinition.setServiceName(definition.getServiceName());
        previewDefinition.setServiceType(definition.getServiceType());
        previewDefinition.setStatus(definition.getStatus());
        previewDefinition.setSqlTemplate(sqlText);
        previewDefinition.setSqlType("FEDERATED_SQL");
        previewDefinition.setExecutionMode(definition.getExecutionMode());
        previewDefinition.setPlanStatus(definition.getPlanStatus());
        previewDefinition.setCurrentSqlVersion(definition.getCurrentSqlVersion());
        previewDefinition.setVersion(definition.getVersion());
        previewDefinition.setMaxResultRows(definition.getMaxResultRows());
        previewDefinition.setQueryTimeoutSeconds(definition.getQueryTimeoutSeconds());
        previewDefinition.setFederatedQueryTimeoutSeconds(definition.getFederatedQueryTimeoutSeconds());
        List<DataServiceRuntimeSource> runtimeSources = dsSourceRepository.findByServiceId(definition.getId()).stream()
            .map(source -> new DataServiceRuntimeSource(
                source,
                requiredConnection(source.getConnectionId()),
                source.getCatalogId() == null ? null : requiredCatalog(source.getCatalogId())
            ))
            .toList();
        return new DataServiceRuntimeDefinition(
            previewDefinition,
            runtimeSources,
            dsParamRepository.findByServiceId(definition.getId()),
            dsFieldRepository.findByServiceId(definition.getId()),
            dsCachePolicyRepository.findByServiceId(definition.getId())
        );
    }

    private DSConnection requiredConnection(Long connectionId) {
        DSConnection connection = dsConnectionRepository.findById(connectionId);
        if (connection == null) {
            throw new ServiceConfigInvalidException("联邦来源缺少连接配置: " + connectionId);
        }
        return connection;
    }

    private cn.dtkeys.dataservice.datasource.model.DSCatalog requiredCatalog(Long catalogId) {
        cn.dtkeys.dataservice.datasource.model.DSCatalog catalog = dsCatalogRepository.findById(catalogId);
        if (catalog == null) {
            throw new ServiceConfigInvalidException("联邦来源缺少目录配置: " + catalogId);
        }
        return catalog;
    }

    private int resolvePreviewTimeout(DSDefinition definition) {
        return definition.getFederatedQueryTimeoutSeconds() != null && definition.getFederatedQueryTimeoutSeconds() > 0
            ? definition.getFederatedQueryTimeoutSeconds()
            : 10;
    }

    private int resolvePreviewMaxRows(DSDefinition definition) {
        return definition.getMaxResultRows() != null && definition.getMaxResultRows() > 0
            ? definition.getMaxResultRows()
            : 200;
    }

    private FederatedParsedQuery parse(String sqlText) {
        try {
            return federatedSqlParser.parse(sqlText);
        } catch (IllegalArgumentException exception) {
            throw new ServiceConfigInvalidException("联邦 SQL 解析失败: " + exception.getMessage());
        }
    }

    private int resolveNextDraftVersion(DSDefinition definition) {
        int serviceVersion = definition.getVersion() == null ? 0 : definition.getVersion();
        int currentSqlVersion = definition.getCurrentSqlVersion() == null ? 0 : definition.getCurrentSqlVersion();
        return Math.max(serviceVersion, currentSqlVersion) + 1;
    }

    private int resolveDraftVersion(DSDefinition definition, DSSqlText draftSql) {
        if (draftSql != null && draftSql.getVersion() != null) {
            return draftSql.getVersion();
        }
        return definition.getCurrentSqlVersion() == null ? 0 : definition.getCurrentSqlVersion();
    }

    private String resolveExecutionMode(FederatedParsedQuery query) {
        return query.joinQuery() || query.sourceTables().size() > 1 ? "REMOTE_PLUS_LOCAL" : "REMOTE_ONLY";
    }

    private ValidationResult validateRecognizedSources(Long serviceId, ValidationResult validationResult) {
        Set<String> configuredSources = dsSourceRepository.findByServiceId(serviceId).stream()
            .flatMap(source -> java.util.stream.Stream.of(source.getSourceAlias(), source.getSourceValue()))
            .filter(value -> value != null && !value.isBlank())
            .flatMap(value -> java.util.stream.Stream.of(value, stripSchemaPrefix(value)))
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        List<String> unknownSources = validationResult.recognizedSources().stream()
            .filter(source -> !configuredSources.contains(source.trim().toLowerCase(Locale.ROOT))
                && !configuredSources.contains(stripSchemaPrefix(source).trim().toLowerCase(Locale.ROOT)))
            .toList();
        if (unknownSources.isEmpty()) {
            return validationResult;
        }
        List<String> errors = new java.util.ArrayList<>(validationResult.errors());
        errors.add("sql 引用了未显式配置的来源: " + String.join(", ", unknownSources));
        Map<String, Object> stageDetails = new LinkedHashMap<>(validationResult.stageDetails());
        stageDetails.put("configuredSources", configuredSources);
        stageDetails.put("unknownSources", unknownSources);
        return ValidationResult.of(false, List.copyOf(errors), validationResult.warnings(),
            validationResult.recognizedSources(), Map.copyOf(stageDetails));
    }

    private void validateCapability(DSSourceCapability capability, DSConnection connection) {
        if (capability == null) {
            throw new ParamInvalidException("capability 不能为空");
        }
        if (capability.getCapabilityCode() == null || capability.getCapabilityCode().isBlank()) {
            throw new ParamInvalidException("capabilityCode 不能为空");
        }
        if (capability.getCapabilityValue() == null || capability.getCapabilityValue().isBlank()) {
            throw new ParamInvalidException("capabilityValue 不能为空");
        }
        capability.setDbType(connection.getDbType());
        if (capability.getScope() == null || capability.getScope().isBlank()) {
            capability.setScope("GLOBAL");
        }
        if (capability.getEnabled() == null) {
            capability.setEnabled(Boolean.TRUE);
        }
    }

    private String stripSchemaPrefix(String sourceName) {
        String value = sourceName == null ? "" : sourceName.trim();
        int dotIndex = value.lastIndexOf('.');
        return dotIndex >= 0 ? value.substring(dotIndex + 1) : value;
    }

    private void persistValidationLogs(Long serviceId,
                                       int version,
                                       ValidationResult validationResult,
                                       String traceId,
                                       String operator) {
        insertValidateLog(serviceId, version, "PARSE", "PASS", "联邦 SQL 解析完成",
            Map.of("recognizedSources", validationResult.recognizedSources()), traceId, operator);

        insertValidateLog(serviceId, version, "SEMANTIC", validationResult.valid() ? "PASS" : "FAIL",
            validationResult.valid() ? "语义校验通过" : String.join("; ", validationResult.errors()),
            Map.of("errors", validationResult.errors(), "warnings", validationResult.warnings()), traceId, operator);

        insertValidateLog(serviceId, version, "CAPABILITY", "PASS",
            validationResult.warnings().isEmpty() ? "能力校验通过" : "能力校验存在告警",
            Map.of("warnings", validationResult.warnings(), "details", validationResult.stageDetails()), traceId, operator);
    }

    private void persistPlanArtifacts(Long serviceId,
                                      int version,
                                      FederatedPlan plan,
                                      FederatedDiagnostics diagnostics,
                                      String operator) {
        insertPlan(serviceId, version, "LOGICAL", plan.logicalPlan(), diagnostics, plan, operator);
        insertPlan(serviceId, version, "OPTIMIZED", plan.optimizationDecisions(), diagnostics, plan, operator);
        insertPlan(serviceId, version, "SPLIT", diagnostics.stagePlan(), diagnostics, plan, operator);
        insertValidateLog(serviceId, version, "PLAN", "PASS", "联邦 SQL 计划生成完成",
            Map.of(
                "datasourceScope", plan.datasourceScope(),
                "costSummary", plan.costSummary(),
                "statisticsSummary", plan.statisticsSummary()
            ),
            null,
            operator
        );
    }

    private void insertValidateLog(Long serviceId,
                                   int version,
                                   String stage,
                                   String result,
                                   String message,
                                   Map<String, Object> detail,
                                   String traceId,
                                   String operator) {
        DSSqlValidateLog validateLog = new DSSqlValidateLog();
        validateLog.setServiceId(serviceId);
        validateLog.setVersion(version);
        validateLog.setValidateStage(stage);
        validateLog.setResult(result);
        validateLog.setMessage(message);
        validateLog.setDetailJson(toJson(detail));
        validateLog.setTraceId(traceId);
        validateLog.setCreatedBy(operator);
        dsSqlValidateLogRepository.insert(validateLog);
    }

    private void insertPlan(Long serviceId,
                            int version,
                            String planStage,
                            List<String> contentLines,
                            FederatedDiagnostics diagnostics,
                            FederatedPlan plan,
                            String operator) {
        DSSqlPlan sqlPlan = new DSSqlPlan();
        sqlPlan.setServiceId(serviceId);
        sqlPlan.setVersion(version);
        sqlPlan.setPlanStage(planStage);
        sqlPlan.setPlanFormat("JSON");
        sqlPlan.setPlanContent(toJson(contentLines));
        sqlPlan.setStageGraphJson(toJson(Map.of("stages", diagnostics.stagePlan())));
        sqlPlan.setPushdownSummary(toJson(diagnostics.pushdownSummary()));
        sqlPlan.setFallbackReason(toJson(diagnostics.fallbackReasons()));
        sqlPlan.setCostSummary(toJson(plan.costSummary()));
        sqlPlan.setLocalExecutionSummary(toJson(Map.of(
            "executionMode", plan.stages().size() > 1 ? "REMOTE_PLUS_LOCAL" : "REMOTE_ONLY",
            "stageCount", plan.stages().size()
        )));
        sqlPlan.setDatasourceScope(plan.datasourceScope());
        sqlPlan.setCreatedBy(operator);
        dsSqlPlanRepository.insert(sqlPlan);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ServiceConfigInvalidException("联邦元数据序列化失败");
        }
    }

    public record FederatedServiceMetadata(DSDefinition definition,
                                           DSSqlText draftSql,
                                           List<DSSqlValidateLog> validateLogs,
                                           List<DSSqlPlan> plans) {
    }

    public record FederatedPreviewResult(List<Map<String, Object>> rows,
                                         Map<String, Object> meta) {
    }
}
