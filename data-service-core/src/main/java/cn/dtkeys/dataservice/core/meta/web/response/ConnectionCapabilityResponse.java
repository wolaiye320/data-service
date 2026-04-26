package cn.dtkeys.dataservice.core.meta.web.response;

import java.util.List;

public record ConnectionCapabilityResponse(
        Long connectionId,
        String connectionCode,
        String dbType,
        List<ConnectionCapabilityItemResponse> capabilities
) {
}
