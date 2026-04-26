package cn.dtkeys.dataservice.app.api.query;

import cn.dtkeys.dataservice.app.DataServiceApplication;
import cn.dtkeys.dataservice.core.meta.service.FederatedExecutionGuardService;
import cn.dtkeys.dataservice.core.meta.service.QueryResultCache;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = DataServiceApplication.class,
        properties = {
                "data-service.resource-protection.max-concurrent-queries=1",
                "data-service.resource-protection.max-local-comp-intermediate-rows=1"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QueryResourceProtectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CredentialCodec credentialCodec;

    @Autowired
    private FederatedExecutionGuardService federatedExecutionGuardService;

    @Autowired
    private QueryResultCache queryResultCache;

    @BeforeEach
    void cleanTables() {
        queryResultCache.clearAll();
        jdbcTemplate.execute("delete from ds_audit_log");
        jdbcTemplate.execute("delete from ds_dialect_rule");
        jdbcTemplate.execute("delete from ds_source_capability");
        jdbcTemplate.execute("delete from ds_cache_policy");
        jdbcTemplate.execute("delete from ds_service_version");
        jdbcTemplate.execute("delete from ds_service");
        jdbcTemplate.execute("delete from ds_connection");
        jdbcTemplate.execute("drop table if exists public.orders");
        jdbcTemplate.execute("drop table if exists public.order_items");
        jdbcTemplate.execute("create table public.orders (id bigint primary key, order_name varchar(64))");
        jdbcTemplate.execute("create table public.order_items (id bigint primary key, order_id bigint, sku varchar(64))");
        jdbcTemplate.update("insert into public.orders (id, order_name) values (1, 'order-a')");
        jdbcTemplate.update("insert into public.orders (id, order_name) values (2, 'order-b')");
        jdbcTemplate.update("insert into public.order_items (id, order_id, sku) values (10, 1, 'sku-a')");
        jdbcTemplate.update("insert into public.order_items (id, order_id, sku) values (20, 2, 'sku-b')");
        insertEnabledConnection("PG_MAIN");
        insertEnabledConnection("PG_ARCHIVE");
    }

    @Test
    void shouldRejectFederatedQueryWhenConcurrencyPermitAlreadyOccupied() throws Exception {
        insertPublishedFederatedService(
                31L,
                "svc_query_fed_concurrency",
                """
                        select o.id as order_id, oi.sku as item_sku
                        from PG_MAIN@public.orders o
                        join PG_ARCHIVE@public.order_items oi on o.id = oi.order_id
                        where o.id = /* orderId */1
                        """,
                """
                        [{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]
                        """
        );

        try (FederatedExecutionGuardService.GuardPermit ignored = federatedExecutionGuardService.acquire()) {
            mockMvc.perform(post("/api/data-services/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "serviceCode":"svc_query_fed_concurrency",
                                      "inputs":[{"params":{"orderId":1}}],
                                      "requestContext":{"callerId":"caller-a","traceId":"query-fed-concurrency"}
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILURE"))
                    .andExpect(jsonPath("$.items[0].status").value("FAILURE"))
                    .andExpect(jsonPath("$.items[0].error.errorCode").value("INVALID_ARGUMENT"))
                    .andExpect(jsonPath("$.items[0].error.message").value("联邦查询并发超过系统上限: maxConcurrentQueries=1"))
                    .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.failureStage").value("CONCURRENCY_GUARD"))
                    .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.protectionType").value("CONCURRENT_QUERIES"))
                    .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.maxConcurrentQueries").value(1));
        }
    }

    @Test
    void shouldRejectFederatedQueryWhenLocalIntermediateRowsExceedLimit() throws Exception {
        insertPublishedFederatedService(
                32L,
                "svc_query_fed_memory",
                """
                        select o.id as order_id, oi.sku as item_sku
                        from PG_MAIN@public.orders o
                        join PG_ARCHIVE@public.order_items oi on o.id = oi.order_id
                        """,
                "[]"
        );

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_fed_memory",
                                  "inputs":[{"params":{}}],
                                  "requestContext":{"callerId":"caller-a","traceId":"query-fed-memory"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].error.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.items[0].error.message").value("联邦本地整合中间结果超过系统上限: stage=FEDERATED_LOCAL_JOIN, maxIntermediateRows=1, actualRows=2"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.failureStage").value("MEMORY_GUARD"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.protectionType").value("LOCAL_INTERMEDIATE_ROWS"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.guardStage").value("FEDERATED_LOCAL_JOIN"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.maxIntermediateRows").value(1))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.actualRows").value(2));
    }

    private void insertPublishedFederatedService(long serviceId,
                                                 String serviceCode,
                                                 String sqlText,
                                                 String paramSnapshotJson) {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    ?, ?, 'query federated protection service', 'FEDERATED_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """, serviceId, serviceCode);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    ?, ?, 1, 'PUBLISHED', 'FEDERATED_SQL',
                    ?, 
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"},{"connectionCode":"PG_ARCHIVE","schemaName":"public","databaseName":"data_service","tableName":"order_items","alias":"oi","dbType":"POSTGRESQL"}]',
                    ?,
                    '[{"fieldName":"order_id","expression":"o.id","sortOrder":1},{"fieldName":"item_sku","expression":"oi.sku","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"FEDERATED_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """, serviceId, serviceId, sqlText.strip(), paramSnapshotJson);
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
