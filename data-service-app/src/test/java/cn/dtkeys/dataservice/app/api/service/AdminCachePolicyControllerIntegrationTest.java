package cn.dtkeys.dataservice.app.api.service;

import cn.dtkeys.dataservice.app.DataServiceApplication;
import cn.dtkeys.dataservice.core.security.CredentialCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DataServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCachePolicyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CredentialCodec credentialCodec;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("delete from ds_audit_log");
        jdbcTemplate.execute("delete from ds_dialect_rule");
        jdbcTemplate.execute("delete from ds_source_capability");
        jdbcTemplate.execute("delete from ds_cache_policy");
        jdbcTemplate.execute("delete from ds_service_version");
        jdbcTemplate.execute("delete from ds_service");
        jdbcTemplate.execute("delete from ds_connection");
        insertEnabledConnection("PG_MAIN");
        insertDraftService();
    }

    @Test
    void shouldUpsertAndQueryCachePolicy() throws Exception {
        mockMvc.perform(put("/api/admin/services/1/cache-policy")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-cache-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled":true,
                                  "ttlSeconds":300,
                                  "cacheKeyTemplate":"data-service:{serviceCode}:v{version}:{paramHash}:{contextDigest}",
                                  "maxEntries":1000,
                                  "contextKeys":["tenantId","callerId","tenantId"],
                                  "remark":"cache policy"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(1))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.ttlSeconds").value(300))
                .andExpect(jsonPath("$.cacheKeyTemplate").value("data-service:{serviceCode}:v{version}:{paramHash}:{contextDigest}"))
                .andExpect(jsonPath("$.maxEntries").value(1000))
                .andExpect(jsonPath("$.contextKeys", hasSize(2)))
                .andExpect(jsonPath("$.contextKeys[0]").value("tenantId"))
                .andExpect(jsonPath("$.contextKeys[1]").value("callerId"))
                .andExpect(jsonPath("$.remark").value("cache policy"));

        mockMvc.perform(get("/api/admin/services/1/cache-policy")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.ttlSeconds").value(300))
                .andExpect(jsonPath("$.contextKeys", hasSize(2)));
    }

    @Test
    void shouldReturnDisabledDefaultPolicyWhenNotConfigured() throws Exception {
        mockMvc.perform(get("/api/admin/services/1/cache-policy")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(1))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.ttlSeconds").doesNotExist())
                .andExpect(jsonPath("$.cacheKeyTemplate").value("data-service:{serviceCode}:v{version}:{paramHash}"))
                .andExpect(jsonPath("$.contextKeys", hasSize(0)));
    }

    @Test
    void shouldRejectEnabledPolicyWithoutTtlSeconds() throws Exception {
        mockMvc.perform(put("/api/admin/services/1/cache-policy")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled":true,
                                  "contextKeys":["tenantId"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("启用缓存时必须指定 ttlSeconds"));
    }

    @Test
    void shouldClearServiceCacheExplicitly() throws Exception {
        jdbcTemplate.update("""
                update ds_service
                set status = 'PUBLISHED', current_version = 1
                where id = 1
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    1, 1, 1, 'PUBLISHED', 'SIMPLE_SQL',
                    'select 1 as id',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[]',
                    '[{"fieldName":"id","expression":"1","sortOrder":1}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"SIMPLE_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);

        mockMvc.perform(put("/api/admin/services/1/cache-policy")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled":true,
                                  "ttlSeconds":300,
                                  "cacheKeyTemplate":"data-service:{serviceCode}:v{version}:{paramHash}",
                                  "maxEntries":1000,
                                  "contextKeys":[],
                                  "remark":"cache policy"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_cache_policy",
                                  "inputs":[{"params":{}}],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheMissReason").value("MISS"));

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_cache_policy",
                                  "inputs":[{"params":{}}],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(true));

        mockMvc.perform(delete("/api/admin/services/1/cache-policy/cache")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-cache-evict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(1))
                .andExpect(jsonPath("$.serviceCode").value("svc_cache_policy"))
                .andExpect(jsonPath("$.evictedEntries").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.missReason").value("EXPLICIT_EVICT"));

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_cache_policy",
                                  "inputs":[{"params":{}}],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheMissReason").value("EXPLICIT_EVICT"));

        String auditDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'CLEAR_CACHE_POLICY_CACHE' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(auditDetail).contains("\"serviceCode\":\"svc_cache_policy\"");
        org.assertj.core.api.Assertions.assertThat(auditDetail).contains("\"missReason\":\"EXPLICIT_EVICT\"");
    }

    private void insertDraftService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_cache_policy', 'cache policy service', 'SIMPLE_SQL', 'DRAFT', null,
                    'PG_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
    }

    private void insertEnabledConnection(String connectionCode) {
        jdbcTemplate.update("""
                insert into ds_connection (
                    id, connection_code, connection_name, db_type, host, port, username,
                    password_ciphertext, status, remark, connection_config_json, deleted, created_by, updated_by
                ) values (
                    nextval('ds_connection_id_seq'), ?, ?, 'POSTGRESQL', 'localhost', 5432, 'postgres',
                    ?, 'ENABLED', 'test connection', '{"databaseName":"data_service"}', false, 'tester', 'tester'
                )
                """,
                connectionCode,
                connectionCode,
                credentialCodec.encrypt("postgres")
        );
    }
}
