package cn.dtkeys.dataservice.interfaces.dto.admin.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FieldUpsertRequest(
    String sourceAlias,
    @NotBlank(message = "sourceColumn 不能为空") String sourceColumn,
    @NotBlank(message = "fieldName 不能为空") String fieldName,
    @NotBlank(message = "displayName 不能为空") String displayName,
    @NotBlank(message = "fieldType 不能为空") String fieldType,
    @NotNull(message = "sortOrder 不能为空") Integer sortOrder,
    Boolean primaryKey,
    Boolean joinKey,
    String remark
) {
}
