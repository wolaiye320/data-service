package cn.dtkeys.dataservice.web.dto.admin.connection;

import jakarta.validation.constraints.NotBlank;

public record ConnectionStatusUpdateRequest(@NotBlank(message = "status 不能为空") String status) {
}
