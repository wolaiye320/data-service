package cn.dtkeys.dataservice.web.dto.admin.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ParamUpsertRequest(
    @NotBlank(message = "paramName 不能为空") String paramName,
    @NotBlank(message = "displayName 不能为空") String displayName,
    @NotBlank(message = "paramType 不能为空") String paramType,
    @NotBlank(message = "sqlPlaceholder 不能为空") String sqlPlaceholder,
    Boolean required,
    String defaultValue,
    @NotNull(message = "sortOrder 不能为空") Integer sortOrder,
    String remark
) {
}
