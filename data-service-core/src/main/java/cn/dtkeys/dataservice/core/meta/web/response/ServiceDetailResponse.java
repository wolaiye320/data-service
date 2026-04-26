package cn.dtkeys.dataservice.core.meta.web.response;

import java.time.LocalDateTime;
import java.util.List;

public record ServiceDetailResponse(
        Long id,
        String serviceCode,
        String serviceName,
        String sqlType,
        String defaultConnectionCode,
        String tenantId,
        String status,
        Integer currentVersion,
        Integer maxBatchSize,
        Integer maxResultRows,
        Integer queryTimeoutSeconds,
        Integer federatedQueryTimeoutSeconds,
        String remark,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime updatedAt,
        String updatedBy,
        ServiceVersionResponse draftVersion,
        List<ServiceVersionResponse> versionHistory
) {
}
