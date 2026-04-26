package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.meta.web.request.ConnectionPayload;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.Properties;

@Component
public class JdbcConnectionTestExecutor implements ConnectionTestExecutor {

    @Override
    public void test(ConnectionPayload payload) {
        String jdbcUrl = buildJdbcUrl(payload);
        Properties properties = new Properties();
        properties.setProperty("user", payload.username());
        properties.setProperty("password", payload.password());
        try (Connection ignored = DriverManager.getConnection(jdbcUrl, properties)) {
            // 只验证连通性。
        } catch (Exception ex) {
            throw new DataServiceException(ErrorCode.DATASOURCE_CONNECTION_TEST_FAILED, "数据源连接测试失败", ex);
        }
    }

    private String buildJdbcUrl(ConnectionPayload payload) {
        String dbType = payload.dbType().toUpperCase(Locale.ROOT);
        return switch (dbType) {
            case "POSTGRESQL" -> "jdbc:postgresql://" + payload.host() + ":" + payload.port() + "/" + payload.databaseName();
            case "MYSQL" -> "jdbc:mysql://" + payload.host() + ":" + payload.port() + "/" + payload.databaseName();
            case "ORACLE" -> "jdbc:oracle:thin:@" + payload.host() + ":" + payload.port() + "/" + payload.databaseName();
            default -> throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "不支持的数据库类型: " + payload.dbType());
        };
    }
}
