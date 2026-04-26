package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ServiceCreateRequest(
        @NotBlank @Size(max = 64) String serviceCode,
        @NotBlank @Size(max = 128) String serviceName,
        @NotBlank @Size(max = 32) String sqlType,
        @Size(max = 64) String defaultConnectionCode,
        @Size(max = 64) String tenantId,
        @NotBlank String sqlText,
        List<@Valid ParamDefinitionRequest> paramDefinitions,
        Integer maxBatchSize,
        Integer maxResultRows,
        Integer queryTimeoutSeconds,
        Integer federatedQueryTimeoutSeconds,
        @Size(max = 512) String remark
) {

    public ServiceDefinitionRequest toDefinitionRequest() {
        return new ServiceDefinitionRequest(
                serviceName,
                sqlType,
                defaultConnectionCode,
                tenantId,
                sqlText,
                paramDefinitions,
                maxBatchSize,
                maxResultRows,
                queryTimeoutSeconds,
                federatedQueryTimeoutSeconds,
                remark
        );
    }
}
