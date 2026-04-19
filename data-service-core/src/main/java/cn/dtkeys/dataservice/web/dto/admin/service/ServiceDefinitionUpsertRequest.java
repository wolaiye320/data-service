package cn.dtkeys.dataservice.web.dto.admin.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ServiceDefinitionUpsertRequest(
    @NotBlank(message = "serviceCode 不能为空") String serviceCode,
    @NotBlank(message = "serviceName 不能为空") String serviceName,
    @NotBlank(message = "serviceType 不能为空") String serviceType,
    String status,
    String sqlTemplate,
    @NotBlank(message = "sqlType 不能为空") String sqlType,
    String executionMode,
    String planStatus,
    Integer currentSqlVersion,
    Integer version,
    Integer maxBatchSize,
    Integer maxResultRows,
    Integer queryTimeoutSeconds,
    Integer federatedQueryTimeoutSeconds,
    String remark,
    List<@Valid SourceUpsertRequest> sources,
    List<@Valid ParamUpsertRequest> params,
    List<@Valid FieldUpsertRequest> fields
) {
}
