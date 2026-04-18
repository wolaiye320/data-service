package cn.dtkeys.dataservice.web.dto.admin.service;

import jakarta.validation.constraints.NotBlank;

public record ServiceStatusUpdateRequest(@NotBlank String status) {
}
