package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConnectionCapabilityItemRequest(
        @NotBlank @Size(max = 64) String capabilityCode,
        @NotBlank @Size(max = 256) String capabilityValue,
        @Size(max = 4000) String capabilityDetailJson,
        @Size(max = 32) String scope,
        @Size(max = 128) String scopeValue,
        @Size(max = 512) String remark
) {
}
