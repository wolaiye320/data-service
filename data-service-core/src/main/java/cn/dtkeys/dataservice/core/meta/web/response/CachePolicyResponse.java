package cn.dtkeys.dataservice.core.meta.web.response;

import java.time.LocalDateTime;
import java.util.List;

public record CachePolicyResponse(
        Long serviceId,
        boolean enabled,
        Integer ttlSeconds,
        String cacheKeyTemplate,
        Integer maxEntries,
        List<String> contextKeys,
        String remark,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime updatedAt,
        String updatedBy
) {
}
