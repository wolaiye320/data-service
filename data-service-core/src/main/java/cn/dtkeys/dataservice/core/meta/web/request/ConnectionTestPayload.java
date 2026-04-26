package cn.dtkeys.dataservice.core.meta.web.request;

public record ConnectionTestPayload(
        String connectionName,
        String dbType,
        String host,
        Integer port,
        String username,
        String password,
        String databaseName,
        String remark
) implements ConnectionPayload {
}
