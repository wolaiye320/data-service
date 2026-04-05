package cn.dtkeys.dataservice.interfaces.dto.admin.connection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ConnectionUpsertRequest(
    @NotBlank(message = "connectionCode 不能为空") String connectionCode,
    @NotBlank(message = "connectionName 不能为空") String connectionName,
    @NotBlank(message = "dbType 不能为空") String dbType,
    @NotBlank(message = "host 不能为空") String host,
    @NotNull(message = "port 不能为空") Integer port,
    @NotBlank(message = "username 不能为空") String username,
    String passwordCiphertext,
    String status,
    String remark,
    String connectionConfigJson,
    List<@Valid CatalogUpsertRequest> catalogs
) {
}
