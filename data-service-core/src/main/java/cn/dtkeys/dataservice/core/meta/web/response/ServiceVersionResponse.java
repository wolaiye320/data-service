package cn.dtkeys.dataservice.core.meta.web.response;

import java.time.LocalDateTime;
import java.util.List;

public record ServiceVersionResponse(
        Long id,
        Integer version,
        String status,
        String sqlType,
        String sqlText,
        String sourceSnapshotJson,
        List<SourceSnapshotItemResponse> sourceSnapshots,
        String paramSnapshotJson,
        String fieldSnapshotJson,
        String validationSnapshotJson,
        String planSnapshotJson,
        PlanSnapshotViewResponse planSnapshot,
        String requestContextSnapshotJson,
        LocalDateTime publishedAt,
        String publishedBy,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime updatedAt,
        String updatedBy
) {
}
