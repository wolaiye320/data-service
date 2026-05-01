package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsConnectionRepository;
import cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在服务发布前校验资源限制、联邦能力和本地补算边界。
 */
@Service
public class PublishValidationService {

    private final DsConnectionRepository connectionRepository;
    private final SourceCapabilityService sourceCapabilityService;
    private final SourceSnapshotViewService sourceSnapshotViewService;
    private final ExpressionSupportService expressionSupportService;
    private final FederationCapabilityService federationCapabilityService;
    private final FilterPushdownService filterPushdownService;
    private final ProjectionPushdownService projectionPushdownService;
    private final SqlFieldSnapshotService sqlFieldSnapshotService;
    private final LocalCompensationGuardService localCompensationGuardService;

    public PublishValidationService(DsConnectionRepository connectionRepository,
                                    SourceCapabilityService sourceCapabilityService,
                                    SourceSnapshotViewService sourceSnapshotViewService,
                                    ExpressionSupportService expressionSupportService,
                                    FederationCapabilityService federationCapabilityService,
                                    FilterPushdownService filterPushdownService,
                                    ProjectionPushdownService projectionPushdownService,
                                    SqlFieldSnapshotService sqlFieldSnapshotService,
                                    LocalCompensationGuardService localCompensationGuardService) {
        this.connectionRepository = connectionRepository;
        this.sourceCapabilityService = sourceCapabilityService;
        this.sourceSnapshotViewService = sourceSnapshotViewService;
        this.expressionSupportService = expressionSupportService;
        this.federationCapabilityService = federationCapabilityService;
        this.filterPushdownService = filterPushdownService;
        this.projectionPushdownService = projectionPushdownService;
        this.sqlFieldSnapshotService = sqlFieldSnapshotService;
        this.localCompensationGuardService = localCompensationGuardService;
    }

    /**
     * 发布前执行资源限制完整性和联邦能力基础校验。
     */
    public void validate(String sqlType,
                         String sqlText,
                         Integer maxBatchSize,
                         Integer maxResultRows,
                         Integer queryTimeoutSeconds,
                         Integer federatedQueryTimeoutSeconds,
                         String sourceSnapshotJson) {
        validateResourceLimits(maxBatchSize, maxResultRows, queryTimeoutSeconds, federatedQueryTimeoutSeconds);
        if (!"FEDERATED_SQL".equals(sqlType)) {
            return;
        }

        // 联邦 SQL 发布前必须确认每个来源都有能力配置，且补算边界在资源限制内。
        validateFederatedCapabilities(sqlText, sourceSnapshotJson, maxResultRows);
    }

    private void validateResourceLimits(Integer maxBatchSize,
                                        Integer maxResultRows,
                                        Integer queryTimeoutSeconds,
                                        Integer federatedQueryTimeoutSeconds) {
        if (maxBatchSize == null || maxBatchSize <= 0) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "发布要求服务配置 maxBatchSize");
        }
        if (maxResultRows == null || maxResultRows <= 0) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "发布要求服务配置 maxResultRows");
        }
        if (queryTimeoutSeconds == null || queryTimeoutSeconds <= 0) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "发布要求服务配置 queryTimeoutSeconds");
        }
        if (federatedQueryTimeoutSeconds == null || federatedQueryTimeoutSeconds <= 0) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "发布要求服务配置 federatedQueryTimeoutSeconds");
        }
    }

    private void validateFederatedCapabilities(String sqlText, String sourceSnapshotJson, Integer maxResultRows) {
        List<SourceSnapshotItemResponse> sources = sourceSnapshotViewService.toItems(sourceSnapshotJson);
        List<SourceCapabilityService.CapabilitySummary> capabilitySummaries = new java.util.ArrayList<>();
        for (SourceSnapshotItemResponse source : sources) {
            if (source.connectionCode() == null || source.connectionCode().isBlank()) {
                continue;
            }
            DsConnectionRecord connection = connectionRepository.findByConnectionCode(source.connectionCode());
            if (connection == null) {
                continue;
            }
            if (!sourceCapabilityService.hasEnabledCapabilities(connection)) {
                throw new DataServiceException(
                        ErrorCode.INVALID_ARGUMENT,
                        "联邦 SQL 发布前缺少数据源能力配置: connection=" + source.connectionCode()
                );
            }
            capabilitySummaries.add(sourceCapabilityService.summarize(connection));
        }
        federationCapabilityService.validateOrThrow(
                sqlText,
                sources,
                capabilitySummaries,
                expressionSupportService.analyze(sqlText, null)
        );
        localCompensationGuardService.validateOrThrow(
                sqlText,
                sources.size(),
                maxResultRows,
                filterPushdownService.analyze(sqlText, sources, capabilitySummaries),
                projectionPushdownService.analyze(
                        parseFieldSnapshots(sqlText),
                        sources,
                        capabilitySummaries
                )
        );
    }

    private List<SqlFieldSnapshotService.FieldSnapshot> parseFieldSnapshots(String sqlText) {
        String json = sqlFieldSnapshotService.buildFieldSnapshotJson("SIMPLE_SQL", sqlText);
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<SqlFieldSnapshotService.FieldSnapshot>>() {
                    }
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "字段快照反序列化失败", ex);
        }
    }
}
