package cn.dtkeys.dataservice.web.dto.admin.connection;

import jakarta.validation.constraints.NotBlank;

public record CatalogUpsertRequest(
    @NotBlank(message = "catalogType 不能为空") String catalogType,
    @NotBlank(message = "catalogValue 不能为空") String catalogValue
) {
}
