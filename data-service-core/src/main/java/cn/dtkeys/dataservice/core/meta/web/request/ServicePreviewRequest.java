package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.Valid;

import java.util.Map;

public record ServicePreviewRequest(
        Map<String, Object> previewParams,
        @Valid PreviewRequestContext requestContext
) {
}
