package cn.dtkeys.dataservice.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("truncate table ds_audit_log restart identity cascade");
        jdbcTemplate.execute("""
            truncate table ds_sql_validate_log, ds_sql_plan, ds_sql_text, ds_source_capability,
            ds_service_version, ds_cache_policy, ds_field, ds_param, ds_source, ds_catalog, ds_service restart identity cascade
            """);
        jdbcTemplate.execute("truncate table ds_connection restart identity cascade");
    }

    @Test
    void shouldCreateUpdateTestAndDisableConnection() throws Exception {
        String createRequest = """
            {
              "connectionCode": "bank_conn",
              "connectionName": "Bank Connection",
              "dbType": "POSTGRESQL",
              "host": "localhost",
              "port": 5432,
              "username": "postgres",
              "passwordCiphertext": "postgres",
              "status": "ENABLED",
              "connectionConfigJson": "{\\"database\\":\\"data_service\\"}",
              "catalogs": [
                {
                  "catalogCode": "public_schema",
                  "catalogName": "public",
                  "catalogType": "SCHEMA",
                  "catalogValue": "public",
                  "status": "ENABLED"
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/admin/connections")
                .header("X-Operator", "admin-a")
                .header("X-Operator-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.connection.connectionCode").value("bank_conn"))
            .andExpect(jsonPath("$.data.catalogs.length()").value(1));

        mockMvc.perform(get("/api/admin/connections")
                .header("X-Operator", "admin-a")
                .header("X-Operator-Role", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].passwordCiphertext").value("po****es"))
            .andExpect(jsonPath("$.data[0].connectionConfigJson").value("***MASKED***"));

        mockMvc.perform(post("/api/admin/connections/1/test")
                .header("X-Operator", "admin-a")
                .header("X-Operator-Role", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        String updateRequest = """
            {
              "connectionCode": "bank_conn",
              "connectionName": "Bank Connection Updated",
              "dbType": "POSTGRESQL",
              "host": "127.0.0.1",
              "port": 5432,
              "username": "postgres",
              "passwordCiphertext": "",
              "status": "ENABLED",
              "connectionConfigJson": "{\\"database\\":\\"data_service\\"}",
              "catalogs": [
                {
                  "catalogCode": "public_schema",
                  "catalogName": "public",
                  "catalogType": "SCHEMA",
                  "catalogValue": "public",
                  "status": "ENABLED"
                },
                {
                  "catalogCode": "bank_database",
                  "catalogName": "data_service",
                  "catalogType": "DATABASE",
                  "catalogValue": "data_service",
                  "status": "ENABLED"
                }
              ]
            }
            """;

        mockMvc.perform(put("/api/admin/connections/1")
                .header("X-Operator", "admin-a")
                .header("X-Operator-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.connection.connectionName").value("Bank Connection Updated"))
            .andExpect(jsonPath("$.data.catalogs.length()").value(2));

        mockMvc.perform(put("/api/admin/connections/1/status")
                .header("X-Operator", "admin-a")
                .header("X-Operator-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "DISABLED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.connection.status").value("DISABLED"));
    }

    @Test
    void shouldKeepOriginalConnectionConfigWhenMaskedPlaceholderIsSubmittedDuringUpdate() throws Exception {
        jdbcTemplate.execute("""
            insert into ds_connection (connection_code, connection_name, db_type, host, port, username, password_ciphertext,
                                       status, deleted, created_by, updated_by, connection_config_json)
            values ('masked_conn', 'Masked Conn', 'POSTGRESQL', '127.0.0.1', 5432, 'postgres', 'postgres',
                    'ENABLED', false, 'tester', 'tester', '{"database":"data_service"}')
            """);

        mockMvc.perform(put("/api/admin/connections/1")
                .header("X-Operator", "admin-a")
                .header("X-Operator-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "connectionCode": "masked_conn",
                      "connectionName": "Masked Conn Updated",
                      "dbType": "POSTGRESQL",
                      "host": "127.0.0.1",
                      "port": 5432,
                      "username": "postgres",
                      "passwordCiphertext": "",
                      "status": "ENABLED",
                      "connectionConfigJson": "***MASKED***",
                      "catalogs": []
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.connection.connectionName").value("Masked Conn Updated"));

        assertThat(jdbcTemplate.queryForObject(
            "select connection_config_json from ds_connection where id = 1",
            String.class
        )).isEqualTo("{\"database\":\"data_service\"}");
    }

    @Test
    void shouldSupportMultipleCatalogsUnderSingleConnection() throws Exception {
        String createRequest = """
            {
              "connectionCode": "shared_conn",
              "connectionName": "Shared Connection",
              "dbType": "POSTGRESQL",
              "host": "localhost",
              "port": 5432,
              "username": "postgres",
              "passwordCiphertext": "postgres",
              "status": "ENABLED",
              "connectionConfigJson": "{\\"database\\":\\"data_service\\"}",
              "catalogs": [
                {
                  "catalogCode": "public_schema",
                  "catalogName": "public",
                  "catalogType": "SCHEMA",
                  "catalogValue": "public",
                  "status": "ENABLED"
                },
                {
                  "catalogCode": "analytics_schema",
                  "catalogName": "analytics",
                  "catalogType": "SCHEMA",
                  "catalogValue": "public",
                  "status": "ENABLED"
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/admin/connections")
                .header("X-Operator", "admin-a")
                .header("X-Operator-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.connection.connectionCode").value("shared_conn"))
            .andExpect(jsonPath("$.data.catalogs.length()").value(2));

        mockMvc.perform(get("/api/admin/connections/1")
                .header("X-Operator", "admin-a")
                .header("X-Operator-Role", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.catalogs.length()").value(2))
            .andExpect(jsonPath("$.data.catalogs[0].connectionId").value(1))
            .andExpect(jsonPath("$.data.catalogs[1].connectionId").value(1));
    }

    @Test
    void shouldCreateAndUpdateServiceDraftWithSourcesParamsAndFields() throws Exception {
        jdbcTemplate.execute("""
            insert into ds_connection (connection_code, connection_name, db_type, host, port, username, password_ciphertext,
                                       status, deleted, created_by, updated_by, connection_config_json)
            values ('svc_conn', 'Service Conn', 'POSTGRESQL', 'localhost', 5432, 'postgres', 'postgres',
                    'ENABLED', false, 'tester', 'tester', '{"database":"data_service"}')
            """);
        jdbcTemplate.execute("""
            insert into ds_catalog (connection_id, catalog_code, catalog_name, catalog_type, catalog_value, status,
                                    deleted, created_by, updated_by)
            values (1, 'public_schema', 'public', 'SCHEMA', 'public', 'ENABLED', false, 'tester', 'tester')
            """);

        String createRequest = """
            {
              "serviceCode": "customer_profile_draft",
              "serviceName": "客户画像草稿",
              "serviceType": "SIMPLE_QUERY",
              "sqlTemplate": "select customer_id as customer_customer_id from customer_order where customer_id = :customerId",
              "sqlType": "SIMPLE_SQL",
              "executionMode": "REMOTE_ONLY",
              "planStatus": "UNPLANNED",
              "sources": [
                {
                  "connectionId": 1,
                  "catalogId": 1,
                  "sourceAlias": "customer",
                  "sourceType": "TABLE",
                  "sourceValue": "customer_order",
                  "status": "ENABLED"
                }
              ],
              "params": [
                {
                  "paramName": "customerId",
                  "displayName": "客户号",
                  "paramType": "LONG",
                  "sqlPlaceholder": "customerId",
                  "required": true,
                  "sortOrder": 1
                }
              ],
              "fields": [
                {
                  "sourceAlias": "customer",
                  "sourceColumn": "customer_id",
                  "fieldName": "customerId",
                  "displayName": "客户号",
                  "fieldType": "LONG",
                  "sortOrder": 1,
                  "primaryKey": true,
                  "joinKey": true
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/admin/service-definitions")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definition.serviceCode").value("customer_profile_draft"))
            .andExpect(jsonPath("$.data.definition.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.sources.length()").value(1))
            .andExpect(jsonPath("$.data.params.length()").value(1))
            .andExpect(jsonPath("$.data.fields.length()").value(1));

        mockMvc.perform(get("/api/admin/service-definitions")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));

        String updateRequest = """
            {
              "serviceCode": "customer_profile_draft",
              "serviceName": "客户画像草稿更新",
              "serviceType": "SIMPLE_QUERY",
              "sqlTemplate": "select customer_id as customer_customer_id, active as customer_active from customer_order where customer_id = :customerId",
              "sqlType": "SIMPLE_SQL",
              "executionMode": "REMOTE_ONLY",
              "planStatus": "UNPLANNED",
              "sources": [
                {
                  "connectionId": 1,
                  "catalogId": 1,
                  "sourceAlias": "customer",
                  "sourceType": "TABLE",
                  "sourceValue": "customer_order",
                  "status": "ENABLED"
                }
              ],
              "params": [
                {
                  "paramName": "customerId",
                  "displayName": "客户号",
                  "paramType": "LONG",
                  "sqlPlaceholder": "customerId",
                  "required": true,
                  "sortOrder": 1
                }
              ],
              "fields": [
                {
                  "sourceAlias": "customer",
                  "sourceColumn": "customer_id",
                  "fieldName": "customerId",
                  "displayName": "客户号",
                  "fieldType": "LONG",
                  "sortOrder": 1,
                  "primaryKey": true,
                  "joinKey": true
                },
                {
                  "sourceAlias": "customer",
                  "sourceColumn": "active",
                  "fieldName": "active",
                  "displayName": "激活状态",
                  "fieldType": "BOOLEAN",
                  "sortOrder": 2,
                  "primaryKey": false,
                  "joinKey": false
                }
              ]
            }
            """;

        mockMvc.perform(put("/api/admin/service-definitions/1")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definition.serviceName").value("客户画像草稿更新"))
            .andExpect(jsonPath("$.data.fields.length()").value(2));

        mockMvc.perform(post("/api/admin/service-definitions/1/publish")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definition.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.definition.version").value(1))
            .andExpect(jsonPath("$.data.definition.currentSqlVersion").value(1));

        mockMvc.perform(get("/api/admin/service-definitions/1/versions")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].version").value(1))
            .andExpect(jsonPath("$.data[0].status").value("PUBLISHED"));

        mockMvc.perform(put("/api/admin/service-definitions/1/status")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "DISABLED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definition.status").value("DISABLED"));
    }

    @Test
    void shouldMaintainFederatedSqlDraftAndConnectionCapabilities() throws Exception {
        jdbcTemplate.execute("""
            insert into ds_connection (connection_code, connection_name, db_type, host, port, username, password_ciphertext,
                                       status, deleted, created_by, updated_by, connection_config_json)
            values ('fed_pg_conn', 'Federated PG Conn', 'POSTGRESQL', 'localhost', 5432, 'postgres', 'postgres',
                    'ENABLED', false, 'tester', 'tester', '{"database":"data_service"}')
            """);
        jdbcTemplate.execute("""
            insert into ds_connection (connection_code, connection_name, db_type, host, port, username, password_ciphertext,
                                       status, deleted, created_by, updated_by, connection_config_json)
            values ('fed_mysql_conn', 'Federated MySQL Conn', 'localhost', 3306, 'root', 'root',
                    'ENABLED', false, 'tester', 'tester', '{"database":"demo"}')
            """.replace("'localhost', 3306", "'MYSQL', 'localhost', 3306"));
        jdbcTemplate.execute("""
            insert into ds_service (service_code, service_name, service_type, status, sql_template, sql_type,
                                    execution_mode, plan_status, version, deleted, created_by, updated_by)
            values ('federated_customer_query', '联邦客户查询', 'FEDERATED_QUERY', 'DRAFT', 'select 1', 'FEDERATED_SQL',
                    'REMOTE_ONLY', 'UNPLANNED', 0, false, 'tester', 'tester')
            """);
        jdbcTemplate.execute("""
            insert into ds_source (service_id, connection_id, catalog_id, source_alias, source_type, source_value,
                                   join_key, status, deleted, created_by, updated_by)
            values (1, 1, null, 'pg_customer', 'TABLE', 'pg_customer', true, 'ENABLED', false, 'tester', 'tester')
            """);
        jdbcTemplate.execute("""
            insert into ds_source (service_id, connection_id, catalog_id, source_alias, source_type, source_value,
                                   join_key, status, deleted, created_by, updated_by)
            values (1, 2, null, 'mysql_order', 'TABLE', 'mysql_order', true, 'ENABLED', false, 'tester', 'tester')
            """);
        jdbcTemplate.execute("""
            insert into ds_param (service_id, param_name, display_name, param_type, sql_placeholder, required,
                                  sort_order, deleted, created_by, updated_by)
            values (1, 'customerId', '客户号', 'LONG', 'customerId', true, 1, false, 'tester', 'tester')
            """);
        jdbcTemplate.execute("""
            insert into ds_field (service_id, source_alias, source_column, field_name, display_name, field_type,
                                  sort_order, primary_key, join_key, deleted, created_by, updated_by)
            values (1, 'pg_customer', 'id', 'customerId', '客户号', 'LONG', 1, true, true, false, 'tester', 'tester')
            """);

        mockMvc.perform(put("/api/admin/connections/1/capabilities")
                .header("X-Operator", "admin-a")
                .header("X-Operator-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    [
                      {
                        "capabilityCode": "JOIN_INNER",
                        "capabilityValue": "true",
                        "scope": "GLOBAL",
                        "enabled": true
                      }
                    ]
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].capabilityCode").value("JOIN_INNER"));

        mockMvc.perform(put("/api/admin/service-definitions/1/federated-sql")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "federatedSqlText": "select pg_customer.id, mysql_order.customer_id from pg_customer join mysql_order on pg_customer.id = mysql_order.customer_id where pg_customer.id = :customerId",
                      "sqlComment": "federated draft"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definition.sqlType").value("FEDERATED_SQL"))
            .andExpect(jsonPath("$.data.definition.planStatus").value("PLANNED"))
            .andExpect(jsonPath("$.data.draftSql.version").value(1))
            .andExpect(jsonPath("$.data.validateLogs.length()").value(4))
            .andExpect(jsonPath("$.data.plans.length()").value(3));

        mockMvc.perform(get("/api/admin/service-definitions/1/federated-metadata")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definition.serviceCode").value("federated_customer_query"));

        mockMvc.perform(post("/api/admin/service-definitions/1/publish")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definition.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.definition.currentSqlVersion").value(1));

        mockMvc.perform(get("/api/admin/service-definitions/1/versions")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].version").value(1))
            .andExpect(jsonPath("$.data[0].sqlDefinitionJson").value(org.hamcrest.Matchers.containsString("\"federatedSqlVersion\":1")))
            .andExpect(jsonPath("$.data[0].sqlDefinitionJson").value(org.hamcrest.Matchers.containsString("\"federatedSqlStatus\":\"PUBLISHED\"")));
    }

    @Test
    void shouldRecordAuditLogWhenPreviewExecutingFederatedSql() throws Exception {
        jdbcTemplate.execute("""
            create table if not exists fed_customer (
                customer_id bigint not null,
                customer_name varchar(64) not null
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists fed_order (
                customer_id bigint not null,
                order_amount numeric(18, 2) not null
            )
            """);
        jdbcTemplate.execute("truncate table fed_customer");
        jdbcTemplate.execute("truncate table fed_order");
        jdbcTemplate.update("""
            insert into fed_customer (customer_id, customer_name) values (?, ?), (?, ?)
            """, 9001L, "Alice", 9002L, "Bob");
        jdbcTemplate.update("""
            insert into fed_order (customer_id, order_amount) values (?, ?), (?, ?)
            """, 9001L, new java.math.BigDecimal("88.00"), 9002L, new java.math.BigDecimal("99.00"));

        jdbcTemplate.execute("""
            insert into ds_connection (connection_code, connection_name, db_type, host, port, username, password_ciphertext,
                                       status, deleted, created_by, updated_by, connection_config_json)
            values ('fed_preview_pg', 'Federated Preview PG', 'POSTGRESQL', 'localhost', 5432, 'postgres', 'postgres',
                    'ENABLED', false, 'tester', 'tester', '{"database":"data_service"}')
            """);
        jdbcTemplate.execute("""
            insert into ds_catalog (connection_id, catalog_code, catalog_name, catalog_type, catalog_value, status,
                                    deleted, created_by, updated_by)
            values (1, 'public_preview', 'public', 'SCHEMA', 'public', 'ENABLED', false, 'tester', 'tester')
            """);
        jdbcTemplate.execute("""
            insert into ds_service (service_code, service_name, service_type, status, sql_template, sql_type,
                                    execution_mode, plan_status, version, deleted, created_by, updated_by)
            values ('federated_preview_query', '联邦预览查询', 'FEDERATED_QUERY', 'DRAFT', 'select 1', 'FEDERATED_SQL',
                    'REMOTE_PLUS_LOCAL', 'PLANNED', 0, false, 'tester', 'tester')
            """);
        jdbcTemplate.execute("""
            insert into ds_source (service_id, connection_id, catalog_id, source_alias, source_type, source_value,
                                   join_key, status, deleted, created_by, updated_by)
            values (1, 1, 1, 'fed_customer', 'TABLE', 'fed_customer', 'customer_id', 'ENABLED', false, 'tester', 'tester')
            """);
        jdbcTemplate.execute("""
            insert into ds_source (service_id, connection_id, catalog_id, source_alias, source_type, source_value,
                                   join_key, status, deleted, created_by, updated_by)
            values (1, 1, 1, 'fed_order', 'TABLE', 'fed_order', 'customer_id', 'ENABLED', false, 'tester', 'tester')
            """);
        jdbcTemplate.execute("""
            insert into ds_param (service_id, param_name, display_name, param_type, sql_placeholder, required,
                                  sort_order, deleted, created_by, updated_by)
            values (1, 'customerId', '客户号', 'LONG', 'customerId', true, 1, false, 'tester', 'tester')
            """);
        jdbcTemplate.execute("""
            insert into ds_field (service_id, source_alias, source_column, field_name, display_name, field_type,
                                  sort_order, primary_key, join_key, deleted, created_by, updated_by)
            values
            (1, 'fed_customer', 'customer_id', 'customerId', '客户号', 'LONG', 1, true, true, false, 'tester', 'tester'),
            (1, 'fed_customer', 'customer_name', 'customerName', '客户名', 'STRING', 2, false, false, false, 'tester', 'tester'),
            (1, 'fed_order', 'customer_id', 'orderCustomerId', '订单客户号', 'LONG', 3, false, true, false, 'tester', 'tester'),
            (1, 'fed_order', 'order_amount', 'orderAmount', '订单金额', 'DECIMAL', 4, false, false, false, 'tester', 'tester')
            """);

        mockMvc.perform(post("/api/admin/service-definitions/1/federated-preview")
                .header("X-Operator", "dev-preview")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "federatedSqlText": "select fed_customer.customer_id, fed_customer.customer_name, fed_order.order_amount from fed_customer join fed_order on fed_customer.customer_id = fed_order.customer_id where fed_customer.customer_id = :customerId",
                      "params": {
                        "customerId": 9001
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].customerId").value(9001))
            .andExpect(jsonPath("$.data[0].customerName").value("Alice"))
            .andExpect(jsonPath("$.data[0].orderAmount").value(88.00))
            .andExpect(jsonPath("$.meta.executedStageCount").value(2));

        Integer previewAuditCount = jdbcTemplate.queryForObject("""
            select count(*) from ds_audit_log
            where event_type = 'PREVIEW_FEDERATED_SQL'
              and target_id = 'federated_preview_query'
            """, Integer.class);
        assertThat(previewAuditCount).isEqualTo(1);
    }

    @Test
    void shouldPersistAuditLogsForPublishAndDisableService() throws Exception {
        jdbcTemplate.execute("""
            insert into ds_connection (connection_code, connection_name, db_type, host, port, username, password_ciphertext,
                                       status, deleted, created_by, updated_by, connection_config_json)
            values ('svc_conn', 'Service Conn', 'POSTGRESQL', 'localhost', 5432, 'postgres', 'postgres',
                    'ENABLED', false, 'tester', 'tester', '{"database":"data_service"}')
            """);
        jdbcTemplate.execute("""
            insert into ds_catalog (connection_id, catalog_code, catalog_name, catalog_type, catalog_value, status,
                                    deleted, created_by, updated_by)
            values (1, 'public_schema', 'public', 'SCHEMA', 'public', 'ENABLED', false, 'tester', 'tester')
            """);

        mockMvc.perform(post("/api/admin/service-definitions")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(simpleServiceCreateRequest("customer_profile_draft",
                    "select customer_id as customer_customer_id from customer_order where customer_id = :customerId")))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/service-definitions/1/publish")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definition.status").value("PUBLISHED"));

        mockMvc.perform(put("/api/admin/service-definitions/1/status")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "DISABLED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definition.status").value("DISABLED"));

        Integer publishCount = jdbcTemplate.queryForObject("""
            select count(*) from ds_audit_log where event_type = 'PUBLISH_SERVICE' and target_id = 'customer_profile_draft'
            """, Integer.class);
        Integer disableCount = jdbcTemplate.queryForObject("""
            select count(*) from ds_audit_log where event_type = 'DISABLE_SERVICE' and target_id = 'customer_profile_draft'
            """, Integer.class);

        assertThat(publishCount).isEqualTo(1);
        assertThat(disableCount).isEqualTo(1);
    }

    @Test
    void shouldDeleteDisabledServiceAndAllowRecreateWithSameCode() throws Exception {
        jdbcTemplate.execute("""
            insert into ds_connection (connection_code, connection_name, db_type, host, port, username, password_ciphertext,
                                       status, deleted, created_by, updated_by, connection_config_json)
            values ('svc_delete_conn', 'Service Delete Conn', 'POSTGRESQL', 'localhost', 5432, 'postgres', 'postgres',
                    'ENABLED', false, 'tester', 'tester', '{"database":"data_service"}')
            """);
        jdbcTemplate.execute("""
            insert into ds_catalog (connection_id, catalog_code, catalog_name, catalog_type, catalog_value, status,
                                    deleted, created_by, updated_by)
            values (1, 'public_schema', 'public', 'SCHEMA', 'public', 'ENABLED', false, 'tester', 'tester')
            """);

        mockMvc.perform(post("/api/admin/service-definitions")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(simpleServiceCreateRequest("delete_me_service",
                    "select customer_id as customer_customer_id from customer_order where customer_id = :customerId")))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/service-definitions/1")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        Integer activeCount = jdbcTemplate.queryForObject("""
            select count(*) from ds_service where service_code = 'delete_me_service' and deleted = false
            """, Integer.class);
        Integer deletedCount = jdbcTemplate.queryForObject("""
            select count(*) from ds_service where service_code = 'delete_me_service' and deleted = true
            """, Integer.class);
        Integer auditCount = jdbcTemplate.queryForObject("""
            select count(*) from ds_audit_log where event_type = 'DELETE_SERVICE' and target_id = 'delete_me_service'
            """, Integer.class);

        assertThat(activeCount).isEqualTo(0);
        assertThat(deletedCount).isEqualTo(1);
        assertThat(auditCount).isEqualTo(1);

        mockMvc.perform(post("/api/admin/service-definitions")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(simpleServiceCreateRequest("delete_me_service",
                    "select customer_id as customer_customer_id from customer_order where customer_id = :customerId")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definition.serviceCode").value("delete_me_service"));
    }

    @Test
    void shouldKeepPublishedVersionSnapshotAfterDraftMutation() throws Exception {
        jdbcTemplate.execute("""
            insert into ds_connection (connection_code, connection_name, db_type, host, port, username, password_ciphertext,
                                       status, deleted, created_by, updated_by, connection_config_json)
            values ('svc_conn', 'Service Conn', 'POSTGRESQL', 'localhost', 5432, 'postgres', 'postgres',
                    'ENABLED', false, 'tester', 'tester', '{"database":"data_service"}')
            """);
        jdbcTemplate.execute("""
            insert into ds_catalog (connection_id, catalog_code, catalog_name, catalog_type, catalog_value, status,
                                    deleted, created_by, updated_by)
            values (1, 'public_schema', 'public', 'SCHEMA', 'public', 'ENABLED', false, 'tester', 'tester')
            """);

        mockMvc.perform(post("/api/admin/service-definitions")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(simpleServiceCreateRequest("customer_profile_draft",
                    "select customer_id as customer_customer_id from customer_order where customer_id = :customerId")))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/service-definitions/1/publish")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.definition.version").value(1));

        jdbcTemplate.update("""
            update ds_service
            set status = 'DRAFT',
                sql_template = 'select customer_id as customer_customer_id from customer_order where customer_id = 9999'
            where id = 1
            """);

        mockMvc.perform(get("/api/admin/service-definitions/1/versions")
                .header("X-Operator", "dev-a")
                .header("X-Operator-Role", "DEVELOPER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].version").value(1))
            .andExpect(jsonPath("$.data[0].sqlDefinitionJson").value(org.hamcrest.Matchers.containsString(":customerId")));
    }

    @Test
    void shouldPreviewFederatedSqlWhenJoinKeyOnlyConfiguredOnSources() throws Exception {
        jdbcTemplate.execute("""
            create table if not exists fed_customer (
                customer_id bigint not null,
                customer_name varchar(64) not null
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists fed_order (
                customer_id bigint not null,
                order_amount numeric(18, 2) not null
            )
            """);
        jdbcTemplate.execute("truncate table fed_customer");
        jdbcTemplate.execute("truncate table fed_order");
        jdbcTemplate.update("""
            insert into fed_customer (customer_id, customer_name) values (?, ?), (?, ?)
            """, 3001L, "Alice", 3002L, "Bob");
        jdbcTemplate.update("""
            insert into fed_order (customer_id, order_amount) values (?, ?), (?, ?)
            """, 3001L, new java.math.BigDecimal("128.00"), 3002L, new java.math.BigDecimal("256.00"));

        jdbcTemplate.execute("""
            insert into ds_connection (connection_code, connection_name, db_type, host, port, username, password_ciphertext,
                                       status, deleted, created_by, updated_by, connection_config_json)
            values ('fed_preview_create_pg', 'Federated Preview Create PG', 'POSTGRESQL', 'localhost', 5432,
                    'postgres', 'postgres', 'ENABLED', false, 'tester', 'tester', '{"database":"data_service"}')
            """);
        jdbcTemplate.execute("""
            insert into ds_catalog (connection_id, catalog_code, catalog_name, catalog_type, catalog_value, status,
                                    deleted, created_by, updated_by)
            values (1, 'public_preview_create', 'public', 'SCHEMA', 'public', 'ENABLED', false, 'tester', 'tester')
            """);

        mockMvc.perform(post("/api/admin/service-definitions")
                .header("X-Operator", "dev-preview")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "federated_preview_create_query",
                      "serviceName": "联邦预览创建查询",
                      "serviceType": "FEDERATED_QUERY",
                      "status": "DRAFT",
                      "sqlTemplate": "select 1",
                      "sqlType": "FEDERATED_SQL",
                      "executionMode": "REMOTE_PLUS_LOCAL",
                      "planStatus": "UNPLANNED",
                      "currentSqlVersion": 0,
                      "version": 0,
                      "maxBatchSize": 20,
                      "maxResultRows": 200,
                      "queryTimeoutSeconds": 20,
                      "federatedQueryTimeoutSeconds": 40,
                      "sources": [
                        {
                          "connectionId": 1,
                          "catalogId": 1,
                          "sourceAlias": "pg_customer",
                          "sourceType": "TABLE",
                          "sourceValue": "fed_customer",
                          "joinKey": "customer_id",
                          "status": "ENABLED"
                        },
                        {
                          "connectionId": 1,
                          "catalogId": 1,
                          "sourceAlias": "pg_order",
                          "sourceType": "TABLE",
                          "sourceValue": "fed_order",
                          "joinKey": "customer_id",
                          "status": "ENABLED"
                        }
                      ],
                      "params": [
                        {
                          "paramName": "customerId",
                          "displayName": "客户号",
                          "paramType": "LONG",
                          "sqlPlaceholder": "customerId",
                          "required": true,
                          "sortOrder": 1
                        }
                      ],
                      "fields": [
                        {
                          "sourceAlias": "pg_customer",
                          "sourceColumn": "customer_id",
                          "fieldName": "customerId",
                          "displayName": "客户号",
                          "fieldType": "LONG",
                          "sortOrder": 1,
                          "primaryKey": true,
                          "joinKey": false
                        },
                        {
                          "sourceAlias": "pg_customer",
                          "sourceColumn": "customer_name",
                          "fieldName": "customerName",
                          "displayName": "客户名",
                          "fieldType": "STRING",
                          "sortOrder": 2,
                          "primaryKey": false,
                          "joinKey": false
                        },
                        {
                          "sourceAlias": "pg_order",
                          "sourceColumn": "order_amount",
                          "fieldName": "orderAmount",
                          "displayName": "订单金额",
                          "fieldType": "DECIMAL",
                          "sortOrder": 3,
                          "primaryKey": false,
                          "joinKey": false
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sources[0].joinKey").value("customer_id"))
            .andExpect(jsonPath("$.data.sources[1].joinKey").value("customer_id"))
            .andExpect(jsonPath("$.data.fields[0].joinKey").value(false))
            .andExpect(jsonPath("$.data.fields[1].joinKey").value(false))
            .andExpect(jsonPath("$.data.fields[2].joinKey").value(false));

        mockMvc.perform(put("/api/admin/service-definitions/1/federated-sql")
                .header("X-Operator", "dev-preview")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "federatedSqlText": "select pg_customer.customer_id, pg_customer.customer_name, pg_order.order_amount from pg_customer join pg_order on pg_customer.customer_id = pg_order.customer_id where pg_customer.customer_id = :customerId",
                      "sqlComment": "preview source joinKey"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/service-definitions/1/federated-preview")
                .header("X-Operator", "dev-preview")
                .header("X-Operator-Role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "federatedSqlText": "select pg_customer.customer_id, pg_customer.customer_name, pg_order.order_amount from pg_customer join pg_order on pg_customer.customer_id = pg_order.customer_id where pg_customer.customer_id = :customerId",
                      "params": {
                        "customerId": 3001
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].customerId").value(3001))
            .andExpect(jsonPath("$.data[0].customerName").value("Alice"))
            .andExpect(jsonPath("$.data[0].orderAmount").value(128.00))
            .andExpect(jsonPath("$.meta.executedStageCount").value(2));
    }

    private String simpleServiceCreateRequest(String serviceCode, String sqlTemplate) {
        return """
            {
              "serviceCode": "%s",
              "serviceName": "客户画像草稿",
              "serviceType": "SIMPLE_QUERY",
              "sqlTemplate": "%s",
              "sqlType": "SIMPLE_SQL",
              "executionMode": "REMOTE_ONLY",
              "planStatus": "UNPLANNED",
              "sources": [
                {
                  "connectionId": 1,
                  "catalogId": 1,
                  "sourceAlias": "customer",
                  "sourceType": "TABLE",
                  "sourceValue": "customer_order",
                  "status": "ENABLED"
                }
              ],
              "params": [
                {
                  "paramName": "customerId",
                  "displayName": "客户号",
                  "paramType": "LONG",
                  "sqlPlaceholder": "customerId",
                  "required": true,
                  "sortOrder": 1
                }
              ],
              "fields": [
                {
                  "sourceAlias": "customer",
                  "sourceColumn": "customer_id",
                  "fieldName": "customerId",
                  "displayName": "客户号",
                  "fieldType": "LONG",
                  "sortOrder": 1,
                  "primaryKey": true,
                  "joinKey": true
                }
              ]
            }
            """.formatted(serviceCode, sqlTemplate.replace("\"", "\\\""));
    }
}
