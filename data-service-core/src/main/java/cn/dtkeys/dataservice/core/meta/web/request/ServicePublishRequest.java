package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.constraints.Size;

public record ServicePublishRequest(
        @Size(max = 512) String validationSnapshotJson,
        @Size(max = 2000) String planSnapshotJson
) {
}
