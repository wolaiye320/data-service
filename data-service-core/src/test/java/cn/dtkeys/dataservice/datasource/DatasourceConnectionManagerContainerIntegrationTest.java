package cn.dtkeys.dataservice.datasource;

import cn.dtkeys.dataservice.datasource.model.DSConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DatasourceConnectionManagerContainerIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4");
    private static final DockerImageName ORACLE_IMAGE =
        DockerImageName.parse("gvenzl/oracle-xe:21-slim-faststart");

    @Container
    static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>(MYSQL_IMAGE)
        .withDatabaseName("crm")
        .withUsername("test_user")
        .withPassword("test_pass");

    @Container
    static final GenericContainer<?> oracleContainer = new GenericContainer<>(ORACLE_IMAGE)
        .withEnv("ORACLE_PASSWORD", "test_pass")
        .withExposedPorts(1521)
        .waitingFor(Wait.forLogMessage("(?s).*DATABASE IS READY TO USE!.*", 1))
        .withStartupTimeout(Duration.ofMinutes(8));

    private final DatasourceConnectionManager datasourceConnectionManager =
        new DatasourceConnectionManager(new ObjectMapper());

    @Test
    void shouldConnectToMysqlContainer() throws Exception {
        DSConnection connection = new DSConnection();
        connection.setConnectionCode("mysql_container");
        connection.setDbType("MYSQL");
        connection.setHost(mysqlContainer.getHost());
        connection.setPort(mysqlContainer.getMappedPort(MySQLContainer.MYSQL_PORT));
        connection.setUsername(mysqlContainer.getUsername());
        connection.setPasswordCiphertext(mysqlContainer.getPassword());
        connection.setConnectionConfigJson("""
            {"database":"crm","useSSL":"false","allowPublicKeyRetrieval":"true","serverTimezone":"UTC"}
            """);
        connection.setStatus("ENABLED");

        DataSource dataSource = datasourceConnectionManager.createDirectDataSource(connection, null);
        try (Connection jdbcConnection = dataSource.getConnection()) {
            assertThat(jdbcConnection.isValid(5)).isTrue();
            assertThat(jdbcConnection.getMetaData().getDatabaseProductName()).containsIgnoringCase("mysql");
        }
    }

    @Test
    void shouldConnectToOracleContainerWithServiceName() throws Exception {
        DSConnection connection = new DSConnection();
        connection.setConnectionCode("oracle_container");
        connection.setDbType("ORACLE");
        connection.setHost(oracleContainer.getHost());
        connection.setPort(oracleContainer.getMappedPort(1521));
        connection.setUsername("system");
        connection.setPasswordCiphertext("test_pass");
        connection.setConnectionConfigJson("""
            {"serviceName":"xe","oracle.net.CONNECT_TIMEOUT":"3000"}
            """);
        connection.setStatus("ENABLED");

        DataSource dataSource = datasourceConnectionManager.createDirectDataSource(connection, null);
        try (Connection jdbcConnection = dataSource.getConnection()) {
            assertThat(jdbcConnection.isValid(5)).isTrue();
            assertThat(jdbcConnection.getMetaData().getDatabaseProductName()).containsIgnoringCase("oracle");
        }
    }
}
