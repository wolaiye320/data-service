package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.datasource.DatasourceConnectionManager;
import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.parser.FederatedSqlParser;
import cn.dtkeys.dataservice.query.executor.NamedParameterQueryExecutor;
import cn.dtkeys.dataservice.query.executor.QueryParameterBinder;
import cn.dtkeys.dataservice.query.executor.QueryResultMapper;
import cn.dtkeys.dataservice.query.executor.SqlReadOnlyValidator;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeDefinition;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeSource;
import cn.dtkeys.dataservice.service.model.DSDefinition;
import cn.dtkeys.dataservice.service.model.DSField;
import cn.dtkeys.dataservice.service.model.DSParam;
import cn.dtkeys.dataservice.service.model.DSSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_FEDERATION_DB_TESTS", matches = "true")
class FederatedRuntimeQueryExecutorLocalIntegrationTest {

    private final FederatedRuntimeQueryExecutor federatedRuntimeQueryExecutor = new FederatedRuntimeQueryExecutor(
        new QueryParameterBinder(),
        new NamedParameterQueryExecutor(new DatasourceConnectionManager(new ObjectMapper())),
        new QueryResultMapper(),
        new SqlReadOnlyValidator()
    );

    private final FederatedSqlParser federatedSqlParser = new FederatedSqlParser();

    @Test
    void shouldQueryAcrossPostgresqlSchemaAndTwoMysqlDatabases() {
        DataServiceRuntimeDefinition runtimeDefinition = runtimeDefinition();
        FederatedParsedQuery parsedQuery = federatedSqlParser.parse("""
            select pg_customer.customer_code, pg_customer.customer_name, mysql_order.amount, mysql_tag.tag_name
            from pg_customer
            join mysql_order on pg_customer.customer_code = mysql_order.customer_code
            join mysql_tag on pg_customer.customer_code = mysql_tag.customer_code
            where pg_customer.customer_code = :customerCode
            """);
        FederatedPlan plan = new FederatedPlan(
            parsedQuery.originalSql(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            "pg_customer,mysql_order,mysql_tag",
            Map.of("maxParallelism", 1)
        );

        FederatedRuntimeQueryExecutor.FederatedRuntimeExecutionResult result = federatedRuntimeQueryExecutor.execute(
            runtimeDefinition,
            parsedQuery,
            plan,
            Map.of("customerCode", "C1001"),
            10,
            20
        );

        assertThat(result.rows()).containsExactlyInAnyOrder(
            projectedRow("C1001", "Alice Zhang", "1200.00", "ENTERPRISE"),
            projectedRow("C1001", "Alice Zhang", "1200.00", "VIP"),
            projectedRow("C1001", "Alice Zhang", "800.00", "ENTERPRISE"),
            projectedRow("C1001", "Alice Zhang", "800.00", "VIP")
        );
        assertThat(result.summary())
            .containsEntry("executedStageCount", 3)
            .containsEntry("stageWaveCount", 3);
    }

    private DataServiceRuntimeDefinition runtimeDefinition() {
        DSDefinition definition = new DSDefinition();
        definition.setServiceCode("federated_postg_mysql_local_test");
        definition.setSqlTemplate("""
            select pg_customer.customer_code, pg_customer.customer_name, mysql_order.amount, mysql_tag.tag_name
            from pg_customer
            join mysql_order on pg_customer.customer_code = mysql_order.customer_code
            join mysql_tag on pg_customer.customer_code = mysql_tag.customer_code
            where pg_customer.customer_code = :customerCode
            """);

        return new DataServiceRuntimeDefinition(
            definition,
            List.of(
                runtimeSource("pg_customer", "fed_test_customer", "customer_code", postgresConnection(), postgresCatalog()),
                runtimeSource("mysql_order", "fed_test_order", "customer_code", mysqlConnection("mysql_federation1"),
                    mysqlCatalog("mysql_federation1")),
                runtimeSource("mysql_tag", "fed_test_customer_tag", "customer_code", mysqlConnection("mysql_federation2"),
                    mysqlCatalog("mysql_federation2"))
            ),
            List.of(param("customerCode", "STRING")),
            List.of(
                field("pg_customer", "customer_code", "customerCode", "STRING", 1, true),
                field("pg_customer", "customer_name", "customerName", "STRING", 2, false),
                field("mysql_order", "customer_code", "orderCustomerCode", "STRING", 3, true),
                field("mysql_order", "amount", "amount", "DECIMAL", 4, false),
                field("mysql_tag", "customer_code", "tagCustomerCode", "STRING", 5, true),
                field("mysql_tag", "tag_name", "tagName", "STRING", 6, false)
            ),
            null
        );
    }

    private DataServiceRuntimeSource runtimeSource(String alias,
                                                   String sourceValue,
                                                   String joinKey,
                                                   DSConnection connection,
                                                   DSCatalog catalog) {
        DSSource source = new DSSource();
        source.setSourceAlias(alias);
        source.setSourceType("TABLE");
        source.setSourceValue(sourceValue);
        source.setJoinKey(joinKey);
        source.setStatus("ENABLED");
        return new DataServiceRuntimeSource(source, connection, catalog);
    }

    private DSConnection postgresConnection() {
        DSConnection connection = new DSConnection();
        connection.setId(1L);
        connection.setConnectionCode("postg_federation1_local");
        connection.setConnectionName("postg_federation1 local");
        connection.setDbType("POSTGRESQL");
        connection.setHost(setting("FEDERATION_TEST_PG_HOST", "localhost"));
        connection.setPort(Integer.parseInt(setting("FEDERATION_TEST_PG_PORT", "5432")));
        connection.setUsername(setting("FEDERATION_TEST_PG_USER", "sougen"));
        connection.setPasswordCiphertext(setting("FEDERATION_TEST_PG_PASSWORD", ""));
        connection.setConnectionConfigJson("""
            {"database":"%s"}
            """.formatted(setting("FEDERATION_TEST_PG_DATABASE", "federation")).trim());
        connection.setStatus("ENABLED");
        return connection;
    }

    private DSCatalog postgresCatalog() {
        DSCatalog catalog = new DSCatalog();
        catalog.setId(1L);
        catalog.setCatalogCode("postg_federation1_schema");
        catalog.setCatalogName(setting("FEDERATION_TEST_PG_SCHEMA", "postg_federation1"));
        catalog.setCatalogType("SCHEMA");
        catalog.setCatalogValue(setting("FEDERATION_TEST_PG_SCHEMA", "postg_federation1"));
        catalog.setStatus("ENABLED");
        return catalog;
    }

    private DSConnection mysqlConnection(String database) {
        DSConnection connection = new DSConnection();
        connection.setId("mysql_federation1".equals(database) ? 2L : 3L);
        connection.setConnectionCode(database + "_local");
        connection.setConnectionName(database + " local");
        connection.setDbType("MYSQL");
        connection.setHost(setting("FEDERATION_TEST_MYSQL_HOST", "localhost"));
        connection.setPort(Integer.parseInt(setting("FEDERATION_TEST_MYSQL_PORT", "3306")));
        connection.setUsername(setting("FEDERATION_TEST_MYSQL_USER", "newroot"));
        connection.setPasswordCiphertext(setting("FEDERATION_TEST_MYSQL_PASSWORD", "root#1234"));
        connection.setConnectionConfigJson("""
            {"database":"%s","useSSL":"false","allowPublicKeyRetrieval":"true","serverTimezone":"Asia/Shanghai"}
            """.formatted(database).trim());
        connection.setStatus("ENABLED");
        return connection;
    }

    private DSCatalog mysqlCatalog(String database) {
        DSCatalog catalog = new DSCatalog();
        catalog.setId("mysql_federation1".equals(database) ? 2L : 3L);
        catalog.setCatalogCode(database + "_catalog");
        catalog.setCatalogName(database);
        catalog.setCatalogType("DATABASE");
        catalog.setCatalogValue(database);
        catalog.setStatus("ENABLED");
        return catalog;
    }

    private DSParam param(String name, String type) {
        DSParam param = new DSParam();
        param.setParamName(name);
        param.setDisplayName(name);
        param.setParamType(type);
        param.setSqlPlaceholder(name);
        param.setRequired(true);
        param.setSortOrder(1);
        return param;
    }

    private DSField field(String alias,
                          String sourceColumn,
                          String fieldName,
                          String fieldType,
                          int sortOrder,
                          boolean joinKey) {
        DSField field = new DSField();
        field.setSourceAlias(alias);
        field.setSourceColumn(sourceColumn);
        field.setFieldName(fieldName);
        field.setDisplayName(fieldName);
        field.setFieldType(fieldType);
        field.setSortOrder(sortOrder);
        field.setJoinKey(joinKey);
        field.setPrimaryKey(false);
        field.setDeleted(false);
        return field;
    }

    private Map<String, Object> projectedRow(String customerCode,
                                             String customerName,
                                             String amount,
                                             String tagName) {
        return Map.of(
            "customerCode", customerCode,
            "customerName", customerName,
            "amount", new BigDecimal(amount),
            "tagName", tagName
        );
    }

    private String setting(String key, String defaultValue) {
        String propertyValue = System.getProperty(key);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }
}
