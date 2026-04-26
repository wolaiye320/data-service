package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParamDefinitionRequest(
        @NotBlank @Size(max = 64) String paramName,
        @NotBlank @Size(max = 64) String paramType
) {
}
