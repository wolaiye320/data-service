package cn.dtkeys.dataservice.infrastructure.datasource;

import cn.dtkeys.dataservice.common.exception.DatasourceUnavailableException;
import cn.dtkeys.dataservice.domain.model.DSCatalog;
import cn.dtkeys.dataservice.domain.model.DSConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasourceConnectionManagerTest {

    private final DatasourceConnectionManager datasourceConnectionManager =
        new DatasourceConnectionManager(new ObjectMapper());

    @Test
    void shouldBuildPostgresqlJdbcUrlWithSchema() throws Exception {
        DSConnection connection = buildConnection("POSTGRESQL", "{\"database\":\"meta_db\"}");
        DSCatalog catalog = buildCatalog("SCHEMA", "analytics");

        DriverManagerDataSource dataSource =
            (DriverManagerDataSource) datasourceConnectionManager.createDirectDataSource(connection, catalog);

        assertThat(DriverManager.getDriver(dataSource.getUrl()).getClass().getName()).isEqualTo("org.postgresql.Driver");
        assertThat(dataSource.getUrl()).isEqualTo("jdbc:postgresql://127.0.0.1:5432/meta_db?currentSchema=analytics");
    }

    @Test
    void shouldBuildMysqlJdbcUrlWithDatabaseAndParams() throws Exception {
        DSConnection connection = buildConnection(
            "MYSQL",
            "{\"database\":\"crm\",\"useSSL\":\"false\",\"serverTimezone\":\"Asia/Shanghai\"}"
        );

        DriverManagerDataSource dataSource =
            (DriverManagerDataSource) datasourceConnectionManager.createDirectDataSource(connection, null);

        assertThat(DriverManager.getDriver(dataSource.getUrl()).getClass().getName()).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(dataSource.getUrl()).isEqualTo(
            "jdbc:mysql://127.0.0.1:5432/crm?useSSL=false&serverTimezone=Asia%2FShanghai"
        );
    }

    @Test
    void shouldBuildOracleJdbcUrlWithServiceName() throws Exception {
        DSConnection connection = buildConnection(
            "ORACLE",
            "{\"serviceName\":\"orclpdb1\",\"oracle.net.CONNECT_TIMEOUT\":\"3000\"}"
        );

        DriverManagerDataSource dataSource =
            (DriverManagerDataSource) datasourceConnectionManager.createDirectDataSource(connection, null);

        assertThat(DriverManager.getDriver(dataSource.getUrl()).getClass().getName()).isEqualTo("oracle.jdbc.OracleDriver");
        assertThat(dataSource.getUrl()).isEqualTo(
            "jdbc:oracle:thin:@//127.0.0.1:5432/orclpdb1?oracle.net.CONNECT_TIMEOUT=3000"
        );
    }

    @Test
    void shouldBuildOracleJdbcUrlWithSid() {
        DSConnection connection = buildConnection("ORACLE", "{\"sid\":\"ORCLCDB\"}");

        DriverManagerDataSource dataSource =
            (DriverManagerDataSource) datasourceConnectionManager.createDirectDataSource(connection, null);

        assertThat(dataSource.getUrl()).isEqualTo("jdbc:oracle:thin:@127.0.0.1:5432:ORCLCDB");
    }

    @Test
    void shouldRejectUnsupportedDbType() {
        DSConnection connection = buildConnection("SQLSERVER", "{\"database\":\"demo\"}");

        assertThatThrownBy(() -> datasourceConnectionManager.createDirectDataSource(connection, null))
            .isInstanceOf(DatasourceUnavailableException.class)
            .hasMessageContaining("暂不支持的数据源类型");
    }

    private DSConnection buildConnection(String dbType, String connectionConfigJson) {
        DSConnection connection = new DSConnection();
        connection.setConnectionCode("conn_" + dbType.toLowerCase());
        connection.setDbType(dbType);
        connection.setHost("127.0.0.1");
        connection.setPort(5432);
        connection.setUsername("user");
        connection.setPasswordCiphertext("secret");
        connection.setConnectionConfigJson(connectionConfigJson);
        connection.setStatus("ENABLED");
        return connection;
    }

    private DSCatalog buildCatalog(String catalogType, String catalogValue) {
        DSCatalog catalog = new DSCatalog();
        catalog.setCatalogType(catalogType);
        catalog.setCatalogValue(catalogValue);
        return catalog;
    }
}
