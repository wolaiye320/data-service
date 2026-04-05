package cn.dtkeys.dataservice.infrastructure.datasource;

import cn.dtkeys.dataservice.common.exception.DatasourceUnavailableException;
import cn.dtkeys.dataservice.domain.model.DSCatalog;
import cn.dtkeys.dataservice.domain.model.DSConnection;
import cn.dtkeys.dataservice.domain.runtime.DataServiceRuntimeSource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于元数据构建目标数据源连接。
 */
@Component
public class DatasourceConnectionManager {

    private static final String POSTGRESQL = "POSTGRESQL";
    private static final String MYSQL = "MYSQL";
    private static final String ORACLE = "ORACLE";
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Map<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();

    public DatasourceConnectionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 根据运行时来源获取只读数据源。
     *
     * @param runtimeSource 运行时来源
     * @return 数据源
     */
    public DataSource getDataSource(DataServiceRuntimeSource runtimeSource) {
        DSConnection connection = runtimeSource.connection();
        validateConnection(connection);
        String cacheKey = buildCacheKey(connection, runtimeSource.catalog());
        return dataSourceCache.computeIfAbsent(cacheKey,
            ignored -> createDataSource(connection, runtimeSource.catalog()));
    }

    /**
     * 直接按连接配置创建数据源，用于连接配置连通性测试。
     */
    public DataSource createDirectDataSource(DSConnection connection, DSCatalog catalog) {
        if (connection == null) {
            throw new DatasourceUnavailableException("未找到数据源连接配置");
        }
        return createDataSource(connection, catalog);
    }

    /**
     * 清理运行时数据源缓存，供测试或连接配置变更后强制重建连接使用。
     */
    public void clearCache() {
        dataSourceCache.clear();
    }

    private void validateConnection(DSConnection connection) {
        if (connection == null) {
            throw new DatasourceUnavailableException("未找到数据源连接配置");
        }
        if (!"ENABLED".equalsIgnoreCase(connection.getStatus())) {
            throw new DatasourceUnavailableException("数据源连接未启用: " + connection.getConnectionCode());
        }
    }

    private String buildCacheKey(DSConnection connection, DSCatalog catalog) {
        return connection.getId() + ":" + (catalog == null ? "default" : catalog.getId());
    }

    private DataSource createDataSource(DSConnection connection, DSCatalog catalog) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(resolveDriverClassName(connection.getDbType()));
        dataSource.setUrl(buildJdbcUrl(connection, catalog));
        dataSource.setUsername(connection.getUsername());
        dataSource.setPassword(connection.getPasswordCiphertext());
        return dataSource;
    }

    private String resolveDriverClassName(String dbType) {
        return switch (normalizeDbType(dbType)) {
            case POSTGRESQL -> "org.postgresql.Driver";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case ORACLE -> "oracle.jdbc.OracleDriver";
            default -> throw new DatasourceUnavailableException("暂不支持的数据源类型: " + dbType);
        };
    }

    private String buildJdbcUrl(DSConnection connection, DSCatalog catalog) {
        String dbType = normalizeDbType(connection.getDbType());
        Map<String, String> connectionConfig = parseConnectionConfig(connection.getConnectionConfigJson());
        return switch (dbType) {
            case POSTGRESQL -> buildPostgresqlUrl(connection, catalog, connectionConfig);
            case MYSQL -> buildMysqlUrl(connection, catalog, connectionConfig);
            case ORACLE -> buildOracleUrl(connection, catalog, connectionConfig);
            default -> throw new DatasourceUnavailableException("暂不支持的数据源类型: " + connection.getDbType());
        };
    }

    private Map<String, String> parseConnectionConfig(String connectionConfigJson) {
        if (connectionConfigJson == null || connectionConfigJson.isBlank()) {
            return Map.of();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(connectionConfigJson, STRING_MAP));
        } catch (Exception exception) {
            throw new DatasourceUnavailableException("解析连接扩展配置失败", exception);
        }
    }

    private String buildPostgresqlUrl(DSConnection connection, DSCatalog catalog, Map<String, String> connectionConfig) {
        String database = firstNonBlank(
            connectionConfig.get("database"),
            catalogDatabase(catalog)
        );
        if (database == null) {
            throw new DatasourceUnavailableException("缺少数据库名配置: " + connection.getConnectionCode());
        }
        LinkedHashMap<String, String> queryParams = new LinkedHashMap<>();
        String schema = catalogSchema(catalog);
        if (schema != null) {
            queryParams.put("currentSchema", schema);
        }
        appendAdditionalParams(queryParams, connectionConfig, "database");
        return "jdbc:postgresql://" + connection.getHost() + ":" + connection.getPort() + "/" + database
            + buildQueryString(queryParams);
    }

    private String buildMysqlUrl(DSConnection connection, DSCatalog catalog, Map<String, String> connectionConfig) {
        String database = firstNonBlank(
            connectionConfig.get("database"),
            catalogDatabase(catalog),
            catalogSchema(catalog)
        );
        if (database == null) {
            throw new DatasourceUnavailableException("缺少数据库名配置: " + connection.getConnectionCode());
        }
        LinkedHashMap<String, String> queryParams = new LinkedHashMap<>();
        appendAdditionalParams(queryParams, connectionConfig, "database");
        return "jdbc:mysql://" + connection.getHost() + ":" + connection.getPort() + "/" + database
            + buildQueryString(queryParams);
    }

    private String buildOracleUrl(DSConnection connection, DSCatalog catalog, Map<String, String> connectionConfig) {
        String serviceName = firstNonBlank(
            connectionConfig.get("serviceName"),
            connectionConfig.get("service"),
            connectionConfig.get("database"),
            catalogDatabase(catalog)
        );
        String sid = firstNonBlank(connectionConfig.get("sid"));
        if (serviceName == null && sid == null) {
            throw new DatasourceUnavailableException("缺少 Oracle serviceName 或 sid 配置: " + connection.getConnectionCode());
        }
        LinkedHashMap<String, String> queryParams = new LinkedHashMap<>();
        appendAdditionalParams(queryParams, connectionConfig, "serviceName", "service", "database", "sid");
        String baseUrl = serviceName != null
            ? "jdbc:oracle:thin:@//" + connection.getHost() + ":" + connection.getPort() + "/" + serviceName
            : "jdbc:oracle:thin:@" + connection.getHost() + ":" + connection.getPort() + ":" + sid;
        return baseUrl + buildQueryString(queryParams);
    }

    private void appendAdditionalParams(LinkedHashMap<String, String> queryParams,
                                        Map<String, String> connectionConfig,
                                        String... reservedKeys) {
        for (Map.Entry<String, String> entry : connectionConfig.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank() || isReservedKey(entry.getKey(), reservedKeys)) {
                continue;
            }
            queryParams.put(entry.getKey(), entry.getValue());
        }
    }

    private boolean isReservedKey(String key, String... reservedKeys) {
        for (String reservedKey : reservedKeys) {
            if (reservedKey.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private String buildQueryString(Map<String, String> queryParams) {
        if (queryParams.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (!first) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                .append("=")
                .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return builder.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeDbType(String dbType) {
        if (dbType == null) {
            throw new DatasourceUnavailableException("未配置数据源类型");
        }
        return dbType.trim().toUpperCase();
    }

    private String catalogDatabase(DSCatalog catalog) {
        if (catalog == null) {
            return null;
        }
        return "DATABASE".equalsIgnoreCase(catalog.getCatalogType()) ? catalog.getCatalogValue() : null;
    }

    private String catalogSchema(DSCatalog catalog) {
        if (catalog == null) {
            return null;
        }
        return "SCHEMA".equalsIgnoreCase(catalog.getCatalogType()) ? catalog.getCatalogValue() : null;
    }
}
