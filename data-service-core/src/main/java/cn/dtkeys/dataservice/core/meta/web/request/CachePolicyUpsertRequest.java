package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CachePolicyUpsertRequest(
        @NotNull Boolean enabled,
        @Min(1) @Max(86400) Integer ttlSeconds,
        @Size(max = 128) String cacheKeyTemplate,
        @Min(1) @Max(100000) Integer maxEntries,
        List<@Size(max = 64) String> contextKeys,
        @Size(max = 512) String remark
) {
}
