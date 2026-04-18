package cn.dtkeys.dataservice.web.dto.admin.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SourceUpsertRequest(
    @NotNull(message = "connectionId 不能为空") Long connectionId,
    Long catalogId,
    @NotBlank(message = "sourceAlias 不能为空") String sourceAlias,
    @NotBlank(message = "sourceType 不能为空") String sourceType,
    @NotBlank(message = "sourceValue 不能为空") String sourceValue,
    String joinKey,
    String configJson,
    String status,
    String remark
) {
}
