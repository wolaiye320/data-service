package cn.dtkeys.dataservice.app.api.connection;

import cn.dtkeys.dataservice.app.DataServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DataServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminConnectionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("delete from ds_audit_log");
        jdbcTemplate.execute("delete from ds_source_capability");
        jdbcTemplate.execute("delete from ds_service_version");
        jdbcTemplate.execute("delete from ds_service");
        jdbcTemplate.execute("delete from ds_connection");
    }

    @Test
    void shouldRejectAdminRequestWithoutHeaders() throws Exception {
        mockMvc.perform(get("/api/admin/connections"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'ADMIN_ACCESS_DENIED' and target_type = 'CONNECTION'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void shouldCreateListDetailAndTestConnection() throws Exception {
        String body = """
                {
                  "connectionCode":"PG_LOCAL",
                  "connectionName":"Local PG",
                  "dbType":"POSTGRESQL",
                  "host":"127.0.0.1",
                  "port":5432,
                  "username":"postgres",
                  "password":"postgres",
                  "databaseName":"data_service",
                  "remark":"local test"
                }
                """;

        mockMvc.perform(post("/api/admin/connections")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.connectionCode").value("PG_LOCAL"))
                .andExpect(jsonPath("$.password").value("******"))
                .andExpect(jsonPath("$.username").value("p***s"));

        Long connectionId = jdbcTemplate.queryForObject(
                "select id from ds_connection where connection_code = 'PG_LOCAL'",
                Long.class
        );

        mockMvc.perform(get("/api/admin/connections")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].password").value("******"));

        mockMvc.perform(get("/api/admin/connections/" + connectionId)
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value("1***.0.0.1"));

        mockMvc.perform(post("/api/admin/connections/" + connectionId + "/test")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type in ('CREATE_CONNECTION', 'TEST_CONNECTION')",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(2);
    }

    @Test
    void shouldRejectDisableWhenPublishedServiceStillReferencesConnection() throws Exception {
        jdbcTemplate.update("""
                insert into ds_connection (
                    id, connection_code, connection_name, db_type, host, port, username,
                    password_ciphertext, status, deleted, created_by, updated_by
                ) values (
                    1, 'PG_REF', 'PG Ref', 'POSTGRESQL', '127.0.0.1', 5432, 'postgres',
                    'cipher', 'ENABLED', false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version, deleted, created_by, updated_by
                ) values (
                    1, 'svc_ref', 'svc ref', 'FEDERATED_SQL', 'PUBLISHED', 1, false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 'PUBLISHED', 'FEDERATED_SQL', 'select 1',
                    '[{"connectionCode":"PG_REF"}]', '[]', '[]', 'tester', 'tester'
                )
                """);

        mockMvc.perform(put("/api/admin/connections/1/status")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATASOURCE_DISABLED_IN_USE"));
    }

    @Test
    void shouldUpdateConnectionAndExposeMaskedFieldsOnly() throws Exception {
        mockMvc.perform(post("/api/admin/connections")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "connectionCode":"PG_EDIT",
                                  "connectionName":"Local PG",
                                  "dbType":"POSTGRESQL",
                                  "host":"127.0.0.1",
                                  "port":5432,
                                  "username":"postgres",
                                  "password":"postgres",
                                  "databaseName":"data_service",
                                  "remark":"before"
                                }
                                """))
                .andExpect(status().isCreated());

        Long connectionId = jdbcTemplate.queryForObject(
                "select id from ds_connection where connection_code = 'PG_EDIT'",
                Long.class
        );

        mockMvc.perform(put("/api/admin/connections/" + connectionId)
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "connectionName":"Local PG Updated",
                                  "dbType":"POSTGRESQL",
                                  "host":"localhost",
                                  "port":5432,
                                  "username":"postgres",
                                  "password":"postgres",
                                  "databaseName":"data_service",
                                  "remark":"after"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionName").value("Local PG Updated"))
                .andExpect(jsonPath("$.password").value("******"))
                .andExpect(jsonPath("$.remark").value("after"));
    }

    @Test
    void shouldPersistConnectionAuditWithoutPlainPassword() throws Exception {
        mockMvc.perform(post("/api/admin/connections")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-audit-mask-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "connectionCode":"PG_AUDIT_MASK",
                                  "connectionName":"Local PG",
                                  "dbType":"POSTGRESQL",
                                  "host":"127.0.0.1",
                                  "port":5432,
                                  "username":"postgres",
                                  "password":"postgres",
                                  "databaseName":"data_service",
                                  "remark":"audit mask"
                                }
                                """))
                .andExpect(status().isCreated());

        String detailJson = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'CREATE_CONNECTION' and target_id = 'PG_AUDIT_MASK'",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(detailJson).doesNotContain("postgres");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"connectionCode\":\"PG_AUDIT_MASK\"");

        mockMvc.perform(get("/api/admin/audits")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .param("eventType", "CREATE_CONNECTION")
                        .param("traceId", "trace-audit-mask-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void shouldUpsertConnectionCapabilitiesAndUnlockFederatedPublish() throws Exception {
        insertEnabledConnection(1L, "PG_CAP_LEFT");
        insertEnabledConnection(2L, "PG_CAP_RIGHT");
        insertFederatedDraftService();

        mockMvc.perform(put("/api/admin/connections/1/capabilities")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-capability-left")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capabilities":[
                                    {"capabilityCode":"FILTER_PUSHDOWN","capabilityValue":"SUPPORTED"},
                                    {"capabilityCode":"PROJECT_PUSHDOWN","capabilityValue":"SUPPORTED"},
                                    {"capabilityCode":"JOIN_REORDER","capabilityValue":"SUPPORTED"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionId").value(1))
                .andExpect(jsonPath("$.connectionCode").value("PG_CAP_LEFT"))
                .andExpect(jsonPath("$.capabilities", hasSize(3)))
                .andExpect(jsonPath("$.capabilities[0].capabilityCode").value("FILTER_PUSHDOWN"));

        mockMvc.perform(put("/api/admin/connections/2/capabilities")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-capability-right")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capabilities":[
                                    {"capabilityCode":"FILTER_PUSHDOWN","capabilityValue":"SUPPORTED"},
                                    {"capabilityCode":"PROJECT_PUSHDOWN","capabilityValue":"SUPPORTED"},
                                    {"capabilityCode":"JOIN_REORDER","capabilityValue":"SUPPORTED"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionId").value(2))
                .andExpect(jsonPath("$.connectionCode").value("PG_CAP_RIGHT"))
                .andExpect(jsonPath("$.capabilities", hasSize(3)));

        mockMvc.perform(post("/api/admin/services/1/publish")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validationSnapshotJson":"{}",
                                  "planSnapshotJson":"{}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.versionHistory", hasSize(1)))
                .andExpect(jsonPath("$.versionHistory[0].status").value("PUBLISHED"));

        Integer capabilityCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_source_capability where connection_id in (1, 2)",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(capabilityCount).isEqualTo(6);

        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'UPSERT_CONNECTION_CAPABILITIES'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(2);
    }

    @Test
    void shouldRejectDuplicatedConnectionCapabilityDefinition() throws Exception {
        insertEnabledConnection(1L, "PG_CAP_DUP");

        mockMvc.perform(put("/api/admin/connections/1/capabilities")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capabilities":[
                                    {"capabilityCode":"FILTER_PUSHDOWN","capabilityValue":"SUPPORTED"},
                                    {"capabilityCode":" filter_pushdown ","capabilityValue":"supported"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("连接能力定义重复: FILTER_PUSHDOWN@GLOBAL"));
    }

    private void insertEnabledConnection(Long id, String connectionCode) {
        jdbcTemplate.update("""
                insert into ds_connection (
                    id, connection_code, connection_name, db_type, host, port, username,
                    password_ciphertext, status, deleted, created_by, updated_by
                ) values (
                    ?, ?, ?, 'POSTGRESQL', '127.0.0.1', 5432, 'postgres',
                    'cipher', 'ENABLED', false, 'tester', 'tester'
                )
                """, id, connectionCode, connectionCode);
    }

    private void insertFederatedDraftService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_connection_capability_publish', 'svc connection capability publish',
                    'FEDERATED_SQL', 'DRAFT', null, 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'FEDERATED_SQL',
                    'select o.id as order_id, oi.sku as item_sku from PG_CAP_LEFT@public.orders o join PG_CAP_RIGHT@public.order_items oi on o.id = oi.order_id where o.id = /* orderId */1',
                    '[{"connectionCode":"PG_CAP_LEFT","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"},{"connectionCode":"PG_CAP_RIGHT","schemaName":"public","databaseName":"data_service","tableName":"order_items","alias":"oi","dbType":"POSTGRESQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]',
                    '[{"fieldName":"order_id","expression":"o.id","sortOrder":1},{"fieldName":"item_sku","expression":"oi.sku","sortOrder":2}]',
                    'tester', 'tester'
                )
                """);
    }
}
