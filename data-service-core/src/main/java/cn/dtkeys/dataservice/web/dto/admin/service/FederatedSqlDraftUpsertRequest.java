package cn.dtkeys.dataservice.web.dto.admin.service;

import jakarta.validation.constraints.NotBlank;

public record FederatedSqlDraftUpsertRequest(
    @NotBlank(message = "federatedSqlText 不能为空") String federatedSqlText,
    String sqlComment
) {
}
