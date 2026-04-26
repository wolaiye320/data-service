package cn.dtkeys.dataservice.core.meta.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConnectionUpdateRequest(
        @NotBlank @Size(max = 128) String connectionName,
        @NotBlank @Size(max = 32) String dbType,
        @NotBlank @Size(max = 255) String host,
        @NotNull @Min(1) @Max(65535) Integer port,
        @NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 256) String password,
        @NotBlank @Size(max = 128) String databaseName,
        @Size(max = 512) String remark
) implements ConnectionPayload {
}
