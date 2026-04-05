package cn.dtkeys.dataservice.interfaces.dto.admin.service;

import jakarta.validation.constraints.NotBlank;

public record ServiceStatusUpdateRequest(@NotBlank String status) {
}
