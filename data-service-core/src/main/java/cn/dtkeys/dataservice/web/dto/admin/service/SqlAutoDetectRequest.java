package cn.dtkeys.dataservice.web.dto.admin.service;

import jakarta.validation.constraints.NotBlank;

public record SqlAutoDetectRequest(
    @NotBlank(message = "sqlText 不能为空") String sqlText,
    String sqlType,
    Long defaultConnectionId,
    Long defaultCatalogId
) {
}
