package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ResourceConflictException;
import cn.dtkeys.dataservice.core.error.ResourceNotFoundException;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsCachePolicyRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsCachePolicyRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsConnectionRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsServiceRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsServiceVersionRepository;
import cn.dtkeys.dataservice.core.meta.web.request.ServiceCreateRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ServiceDefinitionRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ServicePublishRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ServicePreviewRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ServiceStatusUpdateRequest;
import cn.dtkeys.dataservice.core.meta.web.request.ServiceUpdateRequest;
import cn.dtkeys.dataservice.core.meta.web.response.PlanSnapshotViewResponse;
import cn.dtkeys.dataservice.core.meta.web.response.ServiceDetailResponse;
import cn.dtkeys.dataservice.core.meta.web.response.ServicePreviewResponse;
import cn.dtkeys.dataservice.core.meta.web.response.ServiceVersionResponse;
import cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse;
import cn.dtkeys.dataservice.core.meta.web.support.OperatorContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ServiceDefinitionService {

    private static final String EMPTY_JSON_ARRAY = "[]";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final DsCachePolicyRepository cachePolicyRepository;
    private final DsConnectionRepository connectionRepository;
    private final DsServiceRepository serviceRepository;
    private final DsServiceVersionRepository serviceVersionRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ReadOnlySqlGuard readOnlySqlGuard;
    private final SqlObjectValidationService sqlObjectValidationService;
    private final DomaSqlTemplateParser domaSqlTemplateParser;
    private final ParamSnapshotService paramSnapshotService;
    private final PreviewGuardService previewGuardService;
    private final PreviewPlanService previewPlanService;
    private final PreviewQueryService previewQueryService;
    private final PublishValidationService publishValidationService;
    private final PublishValidationSnapshotService publishValidationSnapshotService;
    private final PublishPlanService publishPlanService;
    private final PlanSnapshotService planSnapshotService;
    private final SourceCapabilityService sourceCapabilityService;
    private final ExpressionSupportService expressionSupportService;
    private final SqlSourceSnapshotService sqlSourceSnapshotService;
    private final SourceSnapshotViewService sourceSnapshotViewService;
    private final SqlFieldSnapshotService sqlFieldSnapshotService;
    private final SaveValidationSnapshotService saveValidationSnapshotService;

    public ServiceDefinitionService(DsCachePolicyRepository cachePolicyRepository,
                                    DsConnectionRepository connectionRepository,
                                    DsServiceRepository serviceRepository,
                                    DsServiceVersionRepository serviceVersionRepository,
                                    AuditService auditService,
                                    ObjectMapper objectMapper,
                                    ReadOnlySqlGuard readOnlySqlGuard,
                                    SqlObjectValidationService sqlObjectValidationService,
                                    DomaSqlTemplateParser domaSqlTemplateParser,
                                    ParamSnapshotService paramSnapshotService,
                                    PreviewGuardService previewGuardService,
                                    PreviewPlanService previewPlanService,
                                    PreviewQueryService previewQueryService,
                                    PublishValidationService publishValidationService,
                                    PublishValidationSnapshotService publishValidationSnapshotService,
                                    PublishPlanService publishPlanService,
                                    PlanSnapshotService planSnapshotService,
                                    SourceCapabilityService sourceCapabilityService,
                                    ExpressionSupportService expressionSupportService,
                                    SqlSourceSnapshotService sqlSourceSnapshotService,
                                    SourceSnapshotViewService sourceSnapshotViewService,
                                    SqlFieldSnapshotService sqlFieldSnapshotService,
                                    SaveValidationSnapshotService saveValidationSnapshotService) {
        this.cachePolicyRepository = cachePolicyRepository;
        this.connectionRepository = connectionRepository;
        this.serviceRepository = serviceRepository;
        this.serviceVersionRepository = serviceVersionRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.readOnlySqlGuard = readOnlySqlGuard;
        this.sqlObjectValidationService = sqlObjectValidationService;
        this.domaSqlTemplateParser = domaSqlTemplateParser;
        this.paramSnapshotService = paramSnapshotService;
        this.previewGuardService = previewGuardService;
        this.previewPlanService = previewPlanService;
        this.previewQueryService = previewQueryService;
        this.publishValidationService = publishValidationService;
        this.publishValidationSnapshotService = publishValidationSnapshotService;
        this.publishPlanService = publishPlanService;
        this.planSnapshotService = planSnapshotService;
        this.sourceCapabilityService = sourceCapabilityService;
        this.expressionSupportService = expressionSupportService;
        this.sqlSourceSnapshotService = sqlSourceSnapshotService;
        this.sourceSnapshotViewService = sourceSnapshotViewService;
        this.sqlFieldSnapshotService = sqlFieldSnapshotService;
        this.saveValidationSnapshotService = saveValidationSnapshotService;
    }

    /**
     * 创建服务草稿和首个草稿版本。
     */
    @Transactional
    public ServiceDetailResponse create(ServiceCreateRequest request, OperatorContext operatorContext, String traceId) {
        try {
            validateSql(request.toDefinitionRequest());
            DsServiceRecord service = new DsServiceRecord();
            service.setServiceCode(request.serviceCode());
            applyDefinition(service, request.toDefinitionRequest(), operatorContext.operator());
            service.setStatus("DRAFT");
            service.setCurrentVersion(null);
            service.setDeleted(false);
            serviceRepository.insert(service);

            DsServiceVersionRecord draft = newDraft(service.getId(), 1, request.toDefinitionRequest(), operatorContext.operator());
            serviceVersionRepository.insert(draft);
            auditService.recordServiceEvent(
                    service.getId(), "CREATE_SERVICE", request.serviceCode(), "SUCCESS", "新增服务草稿成功",
                    "{\"serviceCode\":\"" + request.serviceCode() + "\",\"version\":1}", operatorContext, traceId
            );
            return detail(service.getId());
        } catch (DuplicateKeyException ex) {
            recordSaveFailureAudit(
                    null,
                    "CREATE_SERVICE",
                    request.serviceCode(),
                    1,
                    "新增服务草稿失败",
                    operatorContext,
                    traceId,
                    "服务编码已存在: " + request.serviceCode(),
                    ex
            );
            throw new ResourceConflictException("服务编码已存在: " + request.serviceCode());
        } catch (RuntimeException ex) {
            recordSaveFailureAudit(
                    null,
                    "CREATE_SERVICE",
                    request.serviceCode(),
                    1,
                    "新增服务草稿失败",
                    operatorContext,
                    traceId,
                    ex.getMessage(),
                    ex
            );
            throw ex;
        }
    }

    /**
     * 编辑服务草稿，不影响已发布版本。
     */
    @Transactional
    public ServiceDetailResponse update(Long id, ServiceUpdateRequest request, OperatorContext operatorContext, String traceId) {
        DsServiceRecord service = null;
        DsServiceVersionRecord latestDraft = null;
        try {
            service = requireService(id);
            validateSql(request.toDefinitionRequest());
            applyDefinition(service, request.toDefinitionRequest(), operatorContext.operator());
            serviceRepository.update(service);

            latestDraft = serviceVersionRepository.findLatestDraftByServiceId(id);
            if (latestDraft == null) {
                int nextVersion = nextVersion(id);
                latestDraft = newDraft(id, nextVersion, request.toDefinitionRequest(), operatorContext.operator());
                serviceVersionRepository.insert(latestDraft);
            } else {
                applyDraft(latestDraft, request.toDefinitionRequest(), operatorContext.operator());
                serviceVersionRepository.updateDraft(latestDraft);
            }

            auditService.recordServiceEvent(
                    id, "UPDATE_SERVICE", service.getServiceCode(), "SUCCESS", "编辑服务草稿成功",
                    "{\"serviceCode\":\"" + service.getServiceCode() + "\",\"version\":" + latestDraft.getVersion() + "}",
                    operatorContext,
                    traceId
            );
            return detail(id);
        } catch (RuntimeException ex) {
            recordSaveFailureAudit(
                    service == null ? null : service.getId(),
                    "UPDATE_SERVICE",
                    service == null ? String.valueOf(id) : service.getServiceCode(),
                    latestDraft == null ? null : latestDraft.getVersion(),
                    "编辑服务草稿失败",
                    operatorContext,
                    traceId,
                    ex.getMessage(),
                    ex
            );
            throw ex;
        }
    }

    /**
     * 停用服务。
     */
    @Transactional
    public ServiceDetailResponse disable(Long id, ServiceStatusUpdateRequest request, OperatorContext operatorContext, String traceId) {
        DsServiceRecord service = null;
        try {
            service = requireService(id);
            validateDisable(service);
            boolean cachePolicyDisabled = disableCachePolicyIfPresent(id, operatorContext.operator());
            serviceRepository.updateStatus(id, request.status(), operatorContext.operator());
            auditService.recordServiceEvent(
                    id,
                    "DISABLE_SERVICE",
                    service.getServiceCode(),
                    "SUCCESS",
                    "停用服务成功",
                    buildDisableAuditDetail(service.getServiceCode(), request.status(), cachePolicyDisabled, null),
                    operatorContext,
                    traceId
            );
            return detail(id);
        } catch (RuntimeException ex) {
            if (service != null) {
                try {
                    auditService.recordServiceEventRequiresNew(
                            id,
                            "DISABLE_SERVICE",
                            service.getServiceCode(),
                            "FAILURE",
                            "停用服务失败",
                            buildDisableAuditDetail(service.getServiceCode(), request.status(), false, ex.getMessage()),
                            operatorContext,
                            traceId
                    );
                } catch (RuntimeException auditEx) {
                    ex.addSuppressed(auditEx);
                }
            }
            throw ex;
        }
    }

    /**
     * 发布当前草稿版本并切换当前版本。
     */
    @Transactional
    public ServiceDetailResponse publish(Long id, ServicePublishRequest request, OperatorContext operatorContext, String traceId) {
        DsServiceRecord service = null;
        DsServiceVersionRecord draft = null;
        try {
            service = requireService(id);
            draft = serviceVersionRepository.findLatestDraftByServiceId(id);
            if (draft == null) {
                throw new ResourceConflictException(ErrorCode.SERVICE_DRAFT_NOT_FOUND, "当前服务不存在可发布的草稿版本");
            }
            validateSql(draft.getSqlType(), draft.getSqlText(), service.getDefaultConnectionCode());
            paramSnapshotService.validateConfirmed(draft.getParamSnapshotJson());
            validateDraftSnapshotsForPublish(service, draft);
            publishValidationService.validate(
                    draft.getSqlType(),
                    draft.getSqlText(),
                    service.getMaxBatchSize(),
                    service.getMaxResultRows(),
                    service.getQueryTimeoutSeconds(),
                    service.getFederatedQueryTimeoutSeconds(),
                    draft.getSourceSnapshotJson()
            );
            DomaSqlTemplateParser.ParseResult parseResult = domaSqlTemplateParser.parse(draft.getSqlText());
            List<SourceSnapshotItemResponse> sourceItems = sourceSnapshotViewService.toItems(draft.getSourceSnapshotJson());
            int sourceCount = sourceItems.size();
            List<SourceCapabilityService.CapabilitySummary> capabilitySummaries = buildCapabilitySummaries(sourceItems);
            ExpressionSupportService.ExpressionSupportSummary expressionSupportSummary =
                    expressionSupportService.validateOrThrow(draft.getSqlText(), resolveExpressionConnection(service, draft));

            LocalDateTime publishedAt = LocalDateTime.now();
            draft.setValidationSnapshotJson(
                    publishValidationSnapshotService.build(
                            draft.getSqlType(),
                            parseResult.params().size(),
                            expressionSupportSummary
                    )
            );
            draft.setPlanSnapshotJson(
                    publishPlanService.build(
                            draft.getSqlType(),
                            draft.getSqlText(),
                            draft.getSourceSnapshotJson(),
                            draft.getFieldSnapshotJson(),
                            sourceCount,
                            parseResult.params().size(),
                            capabilitySummaries
                    )
            );
            draft.setUpdatedBy(operatorContext.operator());
            serviceVersionRepository.updateDraft(draft);
            serviceVersionRepository.clearPublishedState(id, operatorContext.operator());
            serviceVersionRepository.updatePublishState(
                    draft.getId(),
                    STATUS_PUBLISHED,
                    publishedAt,
                    operatorContext.operator(),
                    operatorContext.operator()
            );
            serviceRepository.updateStatus(id, STATUS_PUBLISHED, operatorContext.operator());
            serviceRepository.updateCurrentVersion(id, draft.getVersion(), operatorContext.operator());
            auditService.recordServiceEvent(
                    id,
                    "PUBLISH_SERVICE",
                    service.getServiceCode(),
                    "SUCCESS",
                    "发布服务成功",
                    "{\"serviceCode\":\"" + service.getServiceCode() + "\",\"version\":" + draft.getVersion() + "}",
                    operatorContext,
                    traceId
            );
            return detail(id);
        } catch (RuntimeException ex) {
            if (service != null) {
                try {
                    auditService.recordServiceEventRequiresNew(
                            id,
                            "PUBLISH_SERVICE",
                            service.getServiceCode(),
                            "FAILURE",
                            "发布服务失败",
                            buildPublishAuditDetail(service.getServiceCode(), draft == null ? null : draft.getVersion(), ex.getMessage()),
                            operatorContext,
                            traceId
                    );
                } catch (RuntimeException auditEx) {
                    ex.addSuppressed(auditEx);
                }
            }
            throw ex;
        }
    }

    /**
     * 构建预览执行请求结构。
     */
    public ServicePreviewResponse preview(Long id, ServicePreviewRequest request, OperatorContext operatorContext, String traceId) {
        DsServiceRecord service = requireService(id);
        DsServiceVersionRecord draft = serviceVersionRepository.findLatestDraftByServiceId(id);
        if (draft == null) {
            throw new ResourceConflictException(ErrorCode.SERVICE_DRAFT_NOT_FOUND, "当前服务不存在可预览的草稿版本");
        }
        try {
            previewGuardService.validate(service, draft.getSqlType(), draft.getSqlText(), readOnlySqlGuard);
            validateSql(draft.getSqlType(), draft.getSqlText(), service.getDefaultConnectionCode());
            paramSnapshotService.validateConfirmed(draft.getParamSnapshotJson());
            DomaSqlTemplateParser.ParseResult parseResult = domaSqlTemplateParser.parse(draft.getSqlText());
            String sourceSnapshotJson = sqlSourceSnapshotService.buildSourceSnapshotJson(
                    draft.getSqlType(),
                    draft.getSqlText(),
                    service.getDefaultConnectionCode()
            );
            List<SourceSnapshotItemResponse> sourceItems = sourceSnapshotViewService.toItems(sourceSnapshotJson);
            int sourceCount = sourceItems.size();
            List<SourceCapabilityService.CapabilitySummary> capabilitySummaries = buildCapabilitySummaries(sourceItems);
            String planSnapshotJson = previewPlanService.build(
                    draft.getSqlType(),
                    draft.getSqlText(),
                    sourceSnapshotJson,
                    draft.getFieldSnapshotJson(),
                    parseResult.params().size(),
                    capabilitySummaries
            );
            PreviewQueryService.PreviewQueryResult previewResult = previewQueryService.execute(service, draft, request.previewParams());
            Map<String, Object> requestContext = previewRequestContext(request);
            ServicePreviewResponse response = new ServicePreviewResponse(
                    service.getId(),
                    draft.getVersion(),
                    request.previewParams() == null ? Map.of() : request.previewParams(),
                    requestContext,
                    planSnapshotJson,
                    toPlanSnapshotView(planSnapshotJson),
                    previewResult.rows(),
                    previewResult.elapsedMs(),
                    previewResult.diagnosticSummary()
            );
            auditService.recordServiceEvent(
                    id,
                    "PREVIEW_SERVICE",
                    service.getServiceCode(),
                    "SUCCESS",
                    "预览执行成功",
                    buildPreviewAuditDetail(draft.getVersion(), request.previewParams(), requestContext, previewResult, null),
                    operatorContext,
                    traceId
            );
            return response;
        } catch (RuntimeException ex) {
            auditService.recordServiceEvent(
                    id,
                    "PREVIEW_SERVICE",
                    service.getServiceCode(),
                    "FAILURE",
                    "预览执行失败",
                    buildPreviewAuditDetail(draft.getVersion(), request.previewParams(), previewRequestContext(request), null, ex.getMessage()),
                    operatorContext,
                    traceId
            );
            throw ex;
        }
    }

    /**
     * 查询服务详情。
     */
    public ServiceDetailResponse detail(Long id) {
        DsServiceRecord service = requireService(id);
        return toDetailResponse(service);
    }

    /**
     * 查询服务列表。
     */
    public List<ServiceDetailResponse> list(String status) {
        List<DsServiceRecord> services = status == null || status.isBlank()
                ? serviceRepository.findAll()
                : serviceRepository.findByStatus(status);
        return services.stream().map(this::toDetailResponse).toList();
    }

    private DsServiceRecord requireService(Long id) {
        DsServiceRecord record = serviceRepository.findById(id);
        if (record == null) {
            throw new ResourceNotFoundException("服务不存在: " + id);
        }
        return record;
    }

    private void applyDefinition(DsServiceRecord service, ServiceDefinitionRequest request, String operator) {
        service.setServiceName(request.serviceName());
        service.setSqlType(request.sqlType());
        service.setDefaultConnectionCode(request.defaultConnectionCode());
        service.setTenantId(request.tenantId());
        service.setMaxBatchSize(request.maxBatchSize());
        service.setMaxResultRows(request.maxResultRows());
        service.setQueryTimeoutSeconds(request.queryTimeoutSeconds());
        service.setFederatedQueryTimeoutSeconds(request.federatedQueryTimeoutSeconds());
        service.setRemark(request.remark());
        if (service.getCreatedBy() == null) {
            service.setCreatedBy(operator);
        }
        service.setUpdatedBy(operator);
    }

    private void validateSql(ServiceDefinitionRequest request) {
        validateSql(request.sqlType(), request.sqlText(), request.defaultConnectionCode());
    }

    private void validateSql(String sqlType, String sqlText, String defaultConnectionCode) {
        readOnlySqlGuard.validate(sqlText);
        domaSqlTemplateParser.parse(sqlText);
        validateDefaultConnection(sqlType, defaultConnectionCode);
        sqlSourceSnapshotService.validateFederatedAliases(sqlType, sqlText);
        sqlObjectValidationService.validate(sqlType, sqlText, defaultConnectionCode);
        expressionSupportService.validateOrThrow(sqlText, resolveExpressionConnection(sqlType, defaultConnectionCode));
    }

    private void validateDefaultConnection(String sqlType, String defaultConnectionCode) {
        if (!"SIMPLE_SQL".equals(sqlType)) {
            return;
        }
        if (defaultConnectionCode == null || defaultConnectionCode.isBlank()) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "简单 SQL 必须指定默认连接");
        }
        DsConnectionRecord connection = connectionRepository.findByConnectionCode(defaultConnectionCode);
        if (connection == null) {
            throw new ResourceNotFoundException("默认连接不存在: " + defaultConnectionCode);
        }
        if (!"ENABLED".equals(connection.getStatus())) {
            throw new ResourceConflictException(ErrorCode.DATASOURCE_DISABLED_IN_USE, "默认连接未启用: " + defaultConnectionCode);
        }
    }

    private void validateDisable(DsServiceRecord service) {
        if (STATUS_DISABLED.equals(service.getStatus())) {
            throw new ResourceConflictException(ErrorCode.RESOURCE_CONFLICT, "服务已经是停用状态");
        }
        if (!STATUS_PUBLISHED.equals(service.getStatus())) {
            throw new ResourceConflictException(ErrorCode.RESOURCE_CONFLICT, "仅已发布服务允许停用");
        }
        if (service.getCurrentVersion() == null) {
            throw new ResourceConflictException(ErrorCode.RESOURCE_CONFLICT, "当前服务不存在可停用的发布版本");
        }
    }

    private List<SourceCapabilityService.CapabilitySummary> buildCapabilitySummaries(List<SourceSnapshotItemResponse> sourceItems) {
        return sourceItems.stream()
                .map(SourceSnapshotItemResponse::connectionCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .map(connectionRepository::findByConnectionCode)
                .filter(Objects::nonNull)
                .map(sourceCapabilityService::summarize)
                .toList();
    }

    private boolean disableCachePolicyIfPresent(Long serviceId, String operator) {
        DsCachePolicyRecord cachePolicy = cachePolicyRepository.findByServiceId(serviceId);
        if (cachePolicy == null || !Boolean.TRUE.equals(cachePolicy.getEnabled())) {
            return false;
        }
        cachePolicyRepository.updatePolicy(
                serviceId,
                false,
                cachePolicy.getTtlSeconds(),
                cachePolicy.getCacheKeyTemplate(),
                cachePolicy.getMaxEntries(),
                cachePolicy.getContextKeysJson(),
                cachePolicy.getRemark(),
                operator
        );
        return true;
    }

    private DsServiceVersionRecord newDraft(Long serviceId,
                                            int version,
                                            ServiceDefinitionRequest request,
                                            String operator) {
        DsServiceVersionRecord draft = new DsServiceVersionRecord();
        draft.setServiceId(serviceId);
        draft.setVersion(version);
        draft.setStatus("DRAFT");
        draft.setCreatedBy(operator);
        applyDraft(draft, request, operator);
        return draft;
    }

    private void applyDraft(DsServiceVersionRecord draft, ServiceDefinitionRequest request, String operator) {
        DomaSqlTemplateParser.ParseResult parseResult = domaSqlTemplateParser.parse(request.sqlText());
        String paramSnapshotJson = paramSnapshotService.mergeConfirmedTypes(parseResult, request.paramDefinitions());
        ExpressionSupportService.ExpressionSupportSummary expressionSupportSummary =
                expressionSupportService.validateOrThrow(
                        request.sqlText(),
                        resolveExpressionConnection(request.sqlType(), request.defaultConnectionCode())
                );
        draft.setSqlType(request.sqlType());
        draft.setSqlText(request.sqlText());
        draft.setSourceSnapshotJson(
                sqlSourceSnapshotService.buildSourceSnapshotJson(
                        request.sqlType(),
                        request.sqlText(),
                        request.defaultConnectionCode()
                )
        );
        draft.setParamSnapshotJson(paramSnapshotJson);
        draft.setFieldSnapshotJson(sqlFieldSnapshotService.buildFieldSnapshotJson(request.sqlType(), request.sqlText()));
        draft.setValidationSnapshotJson(
                saveValidationSnapshotService.build(
                        request.sqlType(),
                        parseResult.params().size(),
                        expressionSupportSummary
                )
        );
        draft.setPlanSnapshotJson(null);
        draft.setRequestContextSnapshotJson(null);
        draft.setUpdatedBy(operator);
    }

    private DsConnectionRecord resolveExpressionConnection(DsServiceRecord service, DsServiceVersionRecord draft) {
        return resolveExpressionConnection(draft.getSqlType(), service.getDefaultConnectionCode());
    }

    private DsConnectionRecord resolveExpressionConnection(String sqlType, String defaultConnectionCode) {
        if ("SIMPLE_SQL".equals(sqlType) && defaultConnectionCode != null && !defaultConnectionCode.isBlank()) {
            return connectionRepository.findByConnectionCode(defaultConnectionCode);
        }
        return null;
    }

    private int nextVersion(Long serviceId) {
        return serviceVersionRepository.findHistoryByServiceId(serviceId).stream()
                .map(DsServiceVersionRecord::getVersion)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private ServiceDetailResponse toDetailResponse(DsServiceRecord service) {
        DsServiceVersionRecord draft = serviceVersionRepository.findLatestDraftByServiceId(service.getId());
        List<ServiceVersionResponse> history = serviceVersionRepository.findHistoryByServiceId(service.getId()).stream()
                .map(this::toVersionResponse)
                .toList();
        return new ServiceDetailResponse(
                service.getId(),
                service.getServiceCode(),
                service.getServiceName(),
                service.getSqlType(),
                service.getDefaultConnectionCode(),
                service.getTenantId(),
                service.getStatus(),
                service.getCurrentVersion(),
                service.getMaxBatchSize(),
                service.getMaxResultRows(),
                service.getQueryTimeoutSeconds(),
                service.getFederatedQueryTimeoutSeconds(),
                service.getRemark(),
                service.getCreatedAt(),
                service.getCreatedBy(),
                service.getUpdatedAt(),
                service.getUpdatedBy(),
                draft == null ? null : toVersionResponse(draft),
                history
        );
    }

    private ServiceVersionResponse toVersionResponse(DsServiceVersionRecord version) {
        return new ServiceVersionResponse(
                version.getId(),
                version.getVersion(),
                version.getStatus(),
                version.getSqlType(),
                version.getSqlText(),
                version.getSourceSnapshotJson(),
                sourceSnapshotViewService.toItems(version.getSourceSnapshotJson()),
                version.getParamSnapshotJson(),
                version.getFieldSnapshotJson(),
                version.getValidationSnapshotJson(),
                version.getPlanSnapshotJson(),
                toPlanSnapshotView(version.getPlanSnapshotJson()),
                version.getRequestContextSnapshotJson(),
                version.getPublishedAt(),
                version.getPublishedBy(),
                version.getCreatedAt(),
                version.getCreatedBy(),
                version.getUpdatedAt(),
                version.getUpdatedBy()
        );
    }

    private void validateDraftSnapshotsForPublish(DsServiceRecord service, DsServiceVersionRecord draft) {
        String rebuiltSourceSnapshotJson = sqlSourceSnapshotService.buildSourceSnapshotJson(
                draft.getSqlType(),
                draft.getSqlText(),
                service.getDefaultConnectionCode()
        );
        ensureSnapshotConsistent("来源", draft.getSourceSnapshotJson(), rebuiltSourceSnapshotJson);

        String rebuiltParamSnapshotJson = paramSnapshotService.rebuildConfirmedSnapshot(
                draft.getSqlText(),
                draft.getParamSnapshotJson()
        );
        ensureSnapshotConsistent("参数", draft.getParamSnapshotJson(), rebuiltParamSnapshotJson);

        String rebuiltFieldSnapshotJson = sqlFieldSnapshotService.buildFieldSnapshotJson(draft.getSqlType(), draft.getSqlText());
        ensureSnapshotConsistent("字段", draft.getFieldSnapshotJson(), rebuiltFieldSnapshotJson);
    }

    private void ensureSnapshotConsistent(String snapshotName, String existingJson, String rebuiltJson) {
        if (jsonEquals(existingJson, rebuiltJson)) {
            return;
        }
        throw new DataServiceException(
                ErrorCode.INVALID_ARGUMENT,
                "草稿%s快照与当前 SQL 重新解析结果不一致，请重新保存草稿".formatted(snapshotName)
        );
    }

    private boolean jsonEquals(String left, String right) {
        try {
            JsonNode leftNode = readJsonNode(left);
            JsonNode rightNode = readJsonNode(right);
            return leftNode.equals(rightNode);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "草稿快照比对失败", ex);
        }
    }

    private PlanSnapshotViewResponse toPlanSnapshotView(String planSnapshotJson) {
        PlanSnapshotService.PlanSnapshot snapshot = planSnapshotService.fromJson(planSnapshotJson);
        if (snapshot == null) {
            return null;
        }
        return new PlanSnapshotViewResponse(
                snapshot.stage(),
                snapshot.sqlType(),
                snapshot.sourceCount(),
                snapshot.paramCount(),
                snapshot.stages(),
                snapshot.diagnosticSummary()
        );
    }

    private JsonNode readJsonNode(String json) throws JsonProcessingException {
        String normalized = (json == null || json.isBlank()) ? "null" : json;
        return objectMapper.readTree(normalized);
    }

    private Map<String, Object> previewRequestContext(ServicePreviewRequest request) {
        Map<String, Object> requestContext = new LinkedHashMap<>();
        if (request.requestContext() == null) {
            return requestContext;
        }
        requestContext.put("tenantId", request.requestContext().tenantId());
        requestContext.put("callerId", request.requestContext().callerId());
        requestContext.put("traceId", request.requestContext().traceId());
        requestContext.put("contextKeys", request.requestContext().contextKeys());
        return requestContext;
    }

    private String buildPreviewAuditDetail(Integer draftVersion,
                                           Map<String, Object> previewParams,
                                           Map<String, Object> requestContext,
                                           PreviewQueryService.PreviewQueryResult previewResult,
                                           String errorMessage) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("draftVersion", draftVersion);
        detail.put("previewParamCount", previewParams == null ? 0 : previewParams.size());
        detail.put("previewParamKeys", previewParams == null ? List.of() : previewParams.keySet().stream().toList());
        detail.put("requestContext", requestContext);
        if (previewResult != null) {
            detail.put("elapsedMs", previewResult.elapsedMs());
            detail.put("diagnosticSummary", previewResult.diagnosticSummary());
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            detail.put("errorMessage", errorMessage);
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "预览审计详情序列化失败", ex);
        }
    }

    private String buildPublishAuditDetail(String serviceCode, Integer draftVersion, String errorMessage) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("serviceCode", serviceCode);
        detail.put("draftVersion", draftVersion);
        if (errorMessage != null && !errorMessage.isBlank()) {
            detail.put("errorMessage", errorMessage);
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "发布审计详情序列化失败", ex);
        }
    }

    private void recordSaveFailureAudit(Long serviceId,
                                        String eventType,
                                        String targetId,
                                        Integer draftVersion,
                                        String changeSummary,
                                        OperatorContext operatorContext,
                                        String traceId,
                                        String errorMessage,
                                        RuntimeException sourceException) {
        try {
            auditService.recordServiceEventRequiresNew(
                    serviceId,
                    eventType,
                    targetId,
                    "FAILURE",
                    changeSummary,
                    buildSaveAuditDetail(targetId, draftVersion, errorMessage),
                    operatorContext,
                    traceId
            );
        } catch (RuntimeException auditEx) {
            sourceException.addSuppressed(auditEx);
        }
    }

    private String buildSaveAuditDetail(String serviceCode, Integer draftVersion, String errorMessage) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("serviceCode", serviceCode);
        detail.put("draftVersion", draftVersion);
        if (errorMessage != null && !errorMessage.isBlank()) {
            detail.put("errorMessage", errorMessage);
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "保存审计详情序列化失败", ex);
        }
    }

    private String buildDisableAuditDetail(String serviceCode,
                                           String status,
                                           boolean cachePolicyDisabled,
                                           String errorMessage) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("serviceCode", serviceCode);
        detail.put("status", status);
        detail.put("cachePolicyDisabled", cachePolicyDisabled);
        if (errorMessage != null && !errorMessage.isBlank()) {
            detail.put("errorMessage", errorMessage);
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "停用审计详情序列化失败", ex);
        }
    }
}
