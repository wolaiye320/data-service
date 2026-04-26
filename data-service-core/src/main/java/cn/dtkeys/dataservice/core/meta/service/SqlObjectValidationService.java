package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.ResourceConflictException;
import cn.dtkeys.dataservice.core.error.ResourceNotFoundException;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsConnectionRepository;
import cn.dtkeys.dataservice.core.security.CredentialCodec;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlObjectValidationService {

    private static final Pattern SIMPLE_SQL_PATTERN = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)\\b"
    );
    private static final Pattern FEDERATED_SQL_PATTERN = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([a-zA-Z0-9_]+)@([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)\\b"
    );
    private static final String[] TABLE_TYPES = new String[]{"TABLE", "VIEW"};

    private final DsConnectionRepository connectionRepository;
    private final CredentialCodec credentialCodec;

    public SqlObjectValidationService(DsConnectionRepository connectionRepository, CredentialCodec credentialCodec) {
        this.connectionRepository = connectionRepository;
        this.credentialCodec = credentialCodec;
    }

    /**
     * 校验 SQL 中引用的连接、schema/database、table 是否真实存在。
     */
    public void validate(String sqlType, String sqlText, String defaultConnectionCode) {
        List<SqlObjectRef> refs = extractReferences(sqlType, sqlText, defaultConnectionCode);
        for (SqlObjectRef ref : refs) {
            DsConnectionRecord connectionRecord = requireEnabledConnection(ref.connectionCode());
            try (Connection connection = openConnection(connectionRecord)) {
                validateSchema(connection, connectionRecord, ref);
                validateTable(connection, connectionRecord, ref);
            } catch (DataServiceException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new DataServiceException(
                        ErrorCode.DATASOURCE_CONNECTION_TEST_FAILED,
                        "对象校验连接失败: " + ref.connectionCode(),
                        ex
                );
            }
        }
    }

    private List<SqlObjectRef> extractReferences(String sqlType, String sqlText, String defaultConnectionCode) {
        if ("FEDERATED_SQL".equals(sqlType)) {
            return extractFederatedReferences(sqlText);
        }
        if ("SIMPLE_SQL".equals(sqlType)) {
            return extractSimpleReferences(sqlText, defaultConnectionCode);
        }
        return List.of();
    }

    private List<SqlObjectRef> extractSimpleReferences(String sqlText, String defaultConnectionCode) {
        Matcher matcher = SIMPLE_SQL_PATTERN.matcher(sqlText);
        List<SqlObjectRef> refs = new ArrayList<>();
        while (matcher.find()) {
            refs.add(new SqlObjectRef(defaultConnectionCode, matcher.group(1), matcher.group(2)));
        }
        return refs;
    }

    private List<SqlObjectRef> extractFederatedReferences(String sqlText) {
        Matcher matcher = FEDERATED_SQL_PATTERN.matcher(sqlText);
        List<SqlObjectRef> refs = new ArrayList<>();
        while (matcher.find()) {
            refs.add(new SqlObjectRef(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        return refs;
    }

    private DsConnectionRecord requireEnabledConnection(String connectionCode) {
        DsConnectionRecord connection = connectionRepository.findByConnectionCode(connectionCode);
        if (connection == null) {
            throw new ResourceNotFoundException("SQL 引用的连接不存在: connection=" + connectionCode);
        }
        if (!"ENABLED".equals(connection.getStatus())) {
            throw new ResourceConflictException(
                    ErrorCode.DATASOURCE_DISABLED_IN_USE,
                    "SQL 引用的连接未启用: connection=" + connectionCode
            );
        }
        return connection;
    }

    private Connection openConnection(DsConnectionRecord record) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", record.getUsername());
        properties.setProperty("password", credentialCodec.decrypt(record.getPasswordCiphertext()));
        return DriverManager.getConnection(buildJdbcUrl(record), properties);
    }

    private String buildJdbcUrl(DsConnectionRecord record) {
        String databaseName = extractDatabaseName(record.getConnectionConfigJson());
        String dbType = record.getDbType().toUpperCase(Locale.ROOT);
        return switch (dbType) {
            case "POSTGRESQL" -> "jdbc:postgresql://" + record.getHost() + ":" + record.getPort() + "/" + databaseName;
            case "MYSQL" -> "jdbc:mysql://" + record.getHost() + ":" + record.getPort() + "/" + databaseName;
            case "ORACLE" -> "jdbc:oracle:thin:@" + record.getHost() + ":" + record.getPort() + "/" + databaseName;
            default -> throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "不支持的数据库类型: " + record.getDbType());
        };
    }

    private void validateSchema(Connection connection, DsConnectionRecord record, SqlObjectRef ref) throws Exception {
        if (schemaExists(connection, record.getDbType(), ref.schemaName())) {
            return;
        }
        throw new ResourceNotFoundException(
                "SQL 引用的 schema/database 不存在: connection=%s, schema=%s".formatted(
                        ref.connectionCode(),
                        ref.schemaName()
                )
        );
    }

    private boolean schemaExists(Connection connection, String dbType, String schemaName) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        if ("MYSQL".equalsIgnoreCase(dbType)) {
            try (ResultSet resultSet = metaData.getCatalogs()) {
                while (resultSet.next()) {
                    if (schemaName.equalsIgnoreCase(resultSet.getString("TABLE_CAT"))) {
                        return true;
                    }
                }
            }
            return false;
        }
        try (ResultSet resultSet = metaData.getSchemas()) {
            while (resultSet.next()) {
                if (schemaName.equalsIgnoreCase(resultSet.getString("TABLE_SCHEM"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validateTable(Connection connection, DsConnectionRecord record, SqlObjectRef ref) throws Exception {
        if (tableExists(connection, record.getDbType(), ref)) {
            return;
        }
        throw new ResourceNotFoundException(
                "SQL 引用的 table 不存在: connection=%s, schema=%s, table=%s".formatted(
                        ref.connectionCode(),
                        ref.schemaName(),
                        ref.tableName()
                )
        );
    }

    private boolean tableExists(Connection connection, String dbType, SqlObjectRef ref) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = "MYSQL".equalsIgnoreCase(dbType) ? ref.schemaName() : connection.getCatalog();
        String schema = "MYSQL".equalsIgnoreCase(dbType) ? null : ref.schemaName();
        try (ResultSet resultSet = metaData.getTables(catalog, schema, ref.tableName(), TABLE_TYPES)) {
            return resultSet.next();
        }
    }

    private String extractDatabaseName(String connectionConfigJson) {
        if (connectionConfigJson == null || connectionConfigJson.isBlank()) {
            return "";
        }
        String marker = "\"databaseName\":\"";
        int start = connectionConfigJson.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        int valueEnd = connectionConfigJson.indexOf('"', valueStart);
        return valueEnd > valueStart ? connectionConfigJson.substring(valueStart, valueEnd) : "";
    }

    private record SqlObjectRef(String connectionCode, String schemaName, String tableName) {
    }
}
