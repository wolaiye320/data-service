package cn.dtkeys.dataservice.core.meta.web.response;

public record SourceSnapshotItemResponse(
        String connectionCode,
        String schemaName,
        String databaseName,
        String tableName,
        String alias,
        String dbType
) {
}
