package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record QueryRequestContext(
        @Size(max = 64) String tenantId,
        @Size(max = 64) String callerId,
        @Size(max = 128) String traceId,
        List<@Size(max = 64) String> contextKeys
) {
}
