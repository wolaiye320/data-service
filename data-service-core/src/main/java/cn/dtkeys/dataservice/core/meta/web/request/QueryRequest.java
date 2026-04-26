package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record QueryRequest(
        @NotBlank @Size(max = 128) String serviceCode,
        @NotEmpty List<@Valid QueryInput> inputs,
        @Valid QueryRequestContext requestContext
) {

    public record QueryInput(
            Map<String, Object> params
    ) {
    }
}
