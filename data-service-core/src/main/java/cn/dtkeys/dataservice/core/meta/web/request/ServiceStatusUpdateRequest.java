package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.constraints.Pattern;

public record ServiceStatusUpdateRequest(
        @Pattern(regexp = "DISABLED", message = "status 仅支持 DISABLED")
        String status
) {
}
