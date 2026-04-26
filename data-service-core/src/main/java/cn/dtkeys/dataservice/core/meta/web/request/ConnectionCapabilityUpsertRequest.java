package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ConnectionCapabilityUpsertRequest(
        @NotNull List<@Valid ConnectionCapabilityItemRequest> capabilities
) {
}
