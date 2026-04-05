package cn.dtkeys.dataservice.interfaces.dto.admin.connection;

import jakarta.validation.constraints.NotBlank;

public record SourceCapabilityUpsertRequest(
    @NotBlank(message = "capabilityCode 不能为空") String capabilityCode,
    @NotBlank(message = "capabilityValue 不能为空") String capabilityValue,
    String capabilityDetailJson,
    String scope,
    String scopeValue,
    Boolean enabled,
    String remark
) {
}
