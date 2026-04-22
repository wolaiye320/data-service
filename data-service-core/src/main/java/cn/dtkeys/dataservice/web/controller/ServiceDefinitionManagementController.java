package cn.dtkeys.dataservice.web.controller;

import cn.dtkeys.dataservice.service.ServiceDefinitionManagementService;
import cn.dtkeys.dataservice.service.SqlAutoDetectService;
import cn.dtkeys.dataservice.federation.FederatedMetadataManagementService;
import cn.dtkeys.dataservice.service.model.DSDefinition;
import cn.dtkeys.dataservice.service.model.DSField;
import cn.dtkeys.dataservice.service.model.DSParam;
import cn.dtkeys.dataservice.service.model.DSSource;
import cn.dtkeys.dataservice.security.PermissionCode;
import cn.dtkeys.dataservice.security.RequirePermission;
import cn.dtkeys.dataservice.web.dto.admin.service.FieldUpsertRequest;
import cn.dtkeys.dataservice.web.dto.admin.service.FederatedSqlDraftUpsertRequest;
import cn.dtkeys.dataservice.web.dto.admin.service.FederatedSqlPreviewRequest;
import cn.dtkeys.dataservice.web.dto.admin.service.ParamUpsertRequest;
import cn.dtkeys.dataservice.web.dto.admin.service.ServiceStatusUpdateRequest;
import cn.dtkeys.dataservice.web.dto.admin.service.ServiceDefinitionUpsertRequest;
import cn.dtkeys.dataservice.web.dto.admin.service.ServiceVersionView;
import cn.dtkeys.dataservice.web.dto.admin.service.SqlAutoDetectRequest;
import cn.dtkeys.dataservice.web.dto.admin.service.SqlAutoDetectResponse;
import cn.dtkeys.dataservice.web.dto.admin.service.SourceUpsertRequest;
import cn.dtkeys.dataservice.web.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/admin/service-definitions")
@RequirePermission(PermissionCode.SERVICE_DEFINITION_MANAGE)
public class ServiceDefinitionManagementController {

    private final ServiceDefinitionManagementService serviceDefinitionManagementService;
    private final SqlAutoDetectService sqlAutoDetectService;
    private final FederatedMetadataManagementService federatedMetadataManagementService;

    public ServiceDefinitionManagementController(ServiceDefinitionManagementService serviceDefinitionManagementService,
                                                 SqlAutoDetectService sqlAutoDetectService,
                                                 FederatedMetadataManagementService federatedMetadataManagementService) {
        this.serviceDefinitionManagementService = serviceDefinitionManagementService;
        this.sqlAutoDetectService = sqlAutoDetectService;
        this.federatedMetadataManagementService = federatedMetadataManagementService;
    }

    @PostMapping("/sql-auto-detect")
    public ApiResponse<SqlAutoDetectResponse> autoDetectSql(@Valid @RequestBody SqlAutoDetectRequest request) {
        return ApiResponse.success(sqlAutoDetectService.detect(
            request.sqlText(),
            request.sqlType(),
            request.defaultConnectionId(),
            request.defaultCatalogId()
        ));
    }

    @GetMapping
    public ApiResponse<List<DSDefinition>> list() {
        return ApiResponse.success(serviceDefinitionManagementService.listDefinitions());
    }

    @GetMapping("/{id}")
    public ApiResponse<ServiceDefinitionManagementService.ServiceDefinitionDetail> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(serviceDefinitionManagementService.getDefinition(id));
    }

    @PostMapping
    public ApiResponse<ServiceDefinitionManagementService.ServiceDefinitionDetail> create(
        @Valid @RequestBody ServiceDefinitionUpsertRequest request) {
        return ApiResponse.success(serviceDefinitionManagementService.createDraft(
            toDefinition(request),
            toSources(request.sources()),
            toParams(request.params()),
            toFields(request.fields())
        ));
    }

    @PutMapping("/{id}")
    public ApiResponse<ServiceDefinitionManagementService.ServiceDefinitionDetail> update(
        @PathVariable("id") Long id,
        @Valid @RequestBody ServiceDefinitionUpsertRequest request) {
        return ApiResponse.success(serviceDefinitionManagementService.updateDraft(
            id,
            toDefinition(request),
            toSources(request.sources()),
            toParams(request.params()),
            toFields(request.fields())
        ));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ServiceDefinitionManagementService.ServiceDefinitionDetail> publish(
        @PathVariable("id") Long id) {
        return ApiResponse.success(serviceDefinitionManagementService.publish(id));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<ServiceDefinitionManagementService.ServiceDefinitionDetail> updateStatus(
        @PathVariable("id") Long id,
        @Valid @RequestBody ServiceStatusUpdateRequest request) {
        return ApiResponse.success(serviceDefinitionManagementService.updateStatus(id, request.status()));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<ServiceVersionView>> listVersions(@PathVariable("id") Long id) {
        return ApiResponse.success(serviceDefinitionManagementService.listVersions(id).stream()
            .map(ServiceVersionView::from)
            .toList());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        serviceDefinitionManagementService.deleteDefinition(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/federated-metadata")
    public ApiResponse<FederatedMetadataManagementService.FederatedServiceMetadata> federatedMetadata(
        @PathVariable("id") Long id) {
        return ApiResponse.success(federatedMetadataManagementService.getFederatedMetadata(id));
    }

    @PutMapping("/{id}/federated-sql")
    public ApiResponse<FederatedMetadataManagementService.FederatedServiceMetadata> saveFederatedSqlDraft(
        @PathVariable("id") Long id,
        @Valid @RequestBody FederatedSqlDraftUpsertRequest request) {
        return ApiResponse.success(federatedMetadataManagementService.saveFederatedSqlDraft(
            id,
            request.federatedSqlText(),
            request.sqlComment()
        ));
    }

    @PostMapping("/{id}/federated-preview")
    public ApiResponse<List<java.util.Map<String, Object>>> previewFederatedSql(
        @PathVariable("id") Long id,
        @Valid @RequestBody FederatedSqlPreviewRequest request) {
        FederatedMetadataManagementService.FederatedPreviewResult result =
            federatedMetadataManagementService.previewFederatedSql(id, request.federatedSqlText(), request.params());
        return ApiResponse.success(result.rows(), result.meta());
    }

    private DSDefinition toDefinition(ServiceDefinitionUpsertRequest request) {
        DSDefinition definition = new DSDefinition();
        definition.setServiceCode(request.serviceCode());
        definition.setServiceName(request.serviceName());
        definition.setServiceType(request.serviceType());
        definition.setStatus(request.status());
        definition.setSqlTemplate(request.sqlTemplate());
        definition.setSqlType(request.sqlType());
        definition.setExecutionMode(request.executionMode());
        definition.setPlanStatus(request.planStatus());
        definition.setCurrentSqlVersion(request.currentSqlVersion());
        definition.setVersion(request.version());
        definition.setMaxBatchSize(request.maxBatchSize());
        definition.setMaxResultRows(request.maxResultRows());
        definition.setQueryTimeoutSeconds(request.queryTimeoutSeconds());
        definition.setFederatedQueryTimeoutSeconds(request.federatedQueryTimeoutSeconds());
        definition.setRemark(request.remark());
        return definition;
    }

    private List<DSSource> toSources(List<SourceUpsertRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(request -> {
            DSSource source = new DSSource();
            source.setConnectionId(request.connectionId());
            source.setCatalogId(request.catalogId());
            source.setSourceAlias(request.sourceAlias());
            source.setSourceType(request.sourceType());
            source.setSourceValue(request.sourceValue());
            source.setJoinKey(request.joinKey());
            source.setConfigJson(request.configJson());
            return source;
        }).toList();
    }

    private List<DSParam> toParams(List<ParamUpsertRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(request -> {
            DSParam param = new DSParam();
            param.setParamName(request.paramName());
            param.setDisplayName(request.displayName());
            param.setParamType(request.paramType());
            param.setSqlPlaceholder(request.sqlPlaceholder());
            param.setRequired(request.required());
            param.setDefaultValue(request.defaultValue());
            param.setSortOrder(request.sortOrder());
            return param;
        }).toList();
    }

    private List<DSField> toFields(List<FieldUpsertRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(request -> {
            DSField field = new DSField();
            field.setSourceAlias(request.sourceAlias());
            field.setSourceColumn(request.sourceColumn());
            field.setFieldName(request.fieldName());
            field.setDisplayName(request.displayName());
            field.setFieldType(request.fieldType());
            field.setSortOrder(request.sortOrder());
            field.setPrimaryKey(request.primaryKey());
            field.setJoinKey(request.joinKey());
            return field;
        }).toList();
    }
}
