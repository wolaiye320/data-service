package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConnectionStatusUpdateRequest(
        @NotBlank
        @Pattern(regexp = "ENABLED|DISABLED", message = "status 仅支持 ENABLED 或 DISABLED")
        String status
) {
}
