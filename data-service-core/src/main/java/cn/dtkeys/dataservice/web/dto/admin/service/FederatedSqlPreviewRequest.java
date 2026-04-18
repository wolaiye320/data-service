package cn.dtkeys.dataservice.web.dto.admin.service;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record FederatedSqlPreviewRequest(
    @NotBlank(message = "federatedSqlText 不能为空") String federatedSqlText,
    Map<String, Object> params
) {

    public FederatedSqlPreviewRequest {
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
