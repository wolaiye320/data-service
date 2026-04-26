package cn.dtkeys.dataservice.core.meta.web.response;

import java.time.LocalDateTime;

public record ConnectionDetailResponse(
        Long id,
        String connectionCode,
        String connectionName,
        String dbType,
        String host,
        Integer port,
        String username,
        String password,
        String status,
        String remark,
        String connectionConfigJson,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime updatedAt,
        String updatedBy
) {
}
