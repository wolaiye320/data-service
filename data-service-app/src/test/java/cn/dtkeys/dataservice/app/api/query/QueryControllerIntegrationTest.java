package cn.dtkeys.dataservice.app.api.query;

import cn.dtkeys.dataservice.app.DataServiceApplication;
import cn.dtkeys.dataservice.core.meta.service.QueryResultCache;
import cn.dtkeys.dataservice.core.security.CredentialCodec;
import org.junit.jupiter.api.Assumptions;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

@SpringBootTest(classes = DataServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CredentialCodec credentialCodec;

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
    void shouldQueryPublishedServiceWithSingleAndBatchInputs() throws Exception {
        insertPublishedSimpleService();

        mockMvc.perform(post("/api/data-services/query")
                        .header("X-Trace-Id", "trace-query-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query",
                                  "inputs":[
                                    {"params":{"orderId":1}},
                                    {"params":{"orderId":"bad"}}
                                  ],
                                  "requestContext":{"callerId":"caller-a","traceId":"query-trace-1","contextKeys":["callerId"]}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceCode").value("svc_query"))
                .andExpect(jsonPath("$.serviceVersion").value(1))
                .andExpect(jsonPath("$.status").value("PARTIAL_FAILURE"))
                .andExpect(jsonPath("$.requestContext.callerId").value("caller-a"))
                .andExpect(jsonPath("$.requestContext.traceId").value("query-trace-1"))
                .andExpect(jsonPath("$.requestContext.contextKeys", hasSize(1)))
                .andExpect(jsonPath("$.requestContext.contextSummaryKeys", hasSize(2)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].rows", hasSize(1)))
                .andExpect(jsonPath("$.items[0].rows[0].id").value(1))
                .andExpect(jsonPath("$.items[0].rows[0].order_name").value("order-a"))
                .andExpect(jsonPath("$.items[0].meta.elapsedMs").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.items[0].meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.connectionCode").value("PG_MAIN"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.orderedFields", hasSize(2)))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheKeyTemplate").value("data-service:{serviceCode}:v{version}:{paramHash}"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheNamespaceVersion").value(1))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheMissReason").value("POLICY_DISABLED"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.paramHash").isString())
                .andExpect(jsonPath("$.items[1].status").value("FAILURE"))
                .andExpect(jsonPath("$.items[1].error.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.items[1].meta.elapsedMs").doesNotExist())
                .andExpect(jsonPath("$.items[1].meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[1].meta.diagnosticSummary.failureStage").value("PARAM_VALIDATION"))
                .andExpect(jsonPath("$.items[1].meta.diagnosticSummary.detailCount").value(1))
                .andExpect(jsonPath("$.items[1].error.details", hasSize(1)))
                .andExpect(jsonPath("$.items[1].error.details[0].reasonCode").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.items[1].error.details[0].path").value("inputs[1].params.orderId"))
                .andExpect(jsonPath("$.meta.batch").value(true))
                .andExpect(jsonPath("$.meta.requestedBatchSize").value(2))
                .andExpect(jsonPath("$.meta.successCount").value(1))
                .andExpect(jsonPath("$.meta.failureCount").value(1))
                .andExpect(jsonPath("$.meta.elapsedMs").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.meta.cacheHit").value(false))
                .andExpect(jsonPath("$.meta.traceId").value("query-trace-1"));
    }

    @Test
    void shouldQueryPublishedServiceWithBatchSuccessInputs() throws Exception {
        insertPublishedSimpleService();

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query",
                                  "inputs":[
                                    {"params":{"orderId":1}},
                                    {"params":{"orderId":2}}
                                  ],
                                  "requestContext":{"callerId":"caller-batch-success","traceId":"query-batch-success"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.meta.batch").value(true))
                .andExpect(jsonPath("$.meta.requestedBatchSize").value(2))
                .andExpect(jsonPath("$.meta.successCount").value(2))
                .andExpect(jsonPath("$.meta.failureCount").value(0))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].rows[0].id").value(1))
                .andExpect(jsonPath("$.items[0].rows[0].order_name").value("order-a"))
                .andExpect(jsonPath("$.items[1].status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[1].rows[0].id").value(2))
                .andExpect(jsonPath("$.items[1].rows[0].order_name").value("order-b"));
    }

    @Test
    void shouldQueryPublishedMysqlService() throws Exception {
        Assumptions.assumeTrue(hasMysqlTestCredentials(), "缺少本机 MySQL 测试凭据");
        insertEnabledMysqlConnection("MY_MAIN");
        insertPublishedMysqlService();

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_mysql",
                                  "inputs":[{"params":{"orderId":20001}}],
                                  "requestContext":{"callerId":"caller-mysql","traceId":"query-mysql-success"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].rows", hasSize(1)))
                .andExpect(jsonPath("$.items[0].rows[0].order_id").value(20001))
                .andExpect(jsonPath("$.items[0].rows[0].product_name").value("Risk Scanner"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.connectionCode").value("MY_MAIN"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.orderedFields", hasSize(2)));
    }

    @Test
    void shouldQueryPublishedOracleService() throws Exception {
        Assumptions.assumeTrue(hasOracleTestEnvironment(), "缺少本机 Oracle Docker 测试环境");
        insertEnabledOracleConnection("ORACLE_MAIN");
        insertPublishedOracleService();

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_oracle",
                                  "inputs":[{"params":{"orderId":20001}}],
                                  "requestContext":{"callerId":"caller-oracle","traceId":"query-oracle-success"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].rows", hasSize(1)))
                .andExpect(jsonPath("$.items[0].rows[0].id").value(20001))
                .andExpect(jsonPath("$.items[0].rows[0].order_name").value("oracle-order-a"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.connectionCode").value("ORACLE_MAIN"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.orderedFields", hasSize(2)));
    }

    @Test
    void shouldQueryPublishedPostgresMysqlFederatedService() throws Exception {
        Assumptions.assumeTrue(hasMysqlTestCredentials(), "缺少本机 MySQL 测试凭据");
        jdbcTemplate.update("insert into public.orders (id, order_name) values (20001, 'order-mysql-join')");
        insertEnabledMysqlConnection("MY_MAIN");
        insertPublishedPostgresMysqlFederatedService();

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_pg_mysql_fed",
                                  "inputs":[{"params":{"orderId":20001}}],
                                  "requestContext":{"callerId":"caller-fed-mysql","traceId":"query-fed-mysql-success"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].rows", hasSize(1)))
                .andExpect(jsonPath("$.items[0].rows[0].order_id").value(20001))
                .andExpect(jsonPath("$.items[0].rows[0].product_name").value("Risk Scanner"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.stage").value("FEDERATED_EXECUTION"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.taskCount").value(2))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.taskSummaries", hasSize(2)));
    }

    @Test
    void shouldRejectWhenServiceNotPublished() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_query_draft', 'query draft', 'SIMPLE_SQL', 'DRAFT', null,
                    'PG_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_draft",
                                  "inputs":[{"params":{"orderId":1}}]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("服务未发布: svc_query_draft"));

        mockMvc.perform(post("/api/data-services/query")
                        .header("X-Trace-Id", "trace-query-fallback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_draft",
                                  "inputs":[{"params":{"orderId":1}}],
                                  "requestContext":{"callerId":"   ","contextKeys":["callerId","callerId","traceId"]}
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.traceId").value("trace-query-fallback"));
    }

    @Test
    void shouldRejectWhenCallerIdMissing() throws Exception {
        insertPublishedSimpleService();

        mockMvc.perform(post("/api/data-services/query")
                        .header("X-Trace-Id", "trace-query-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query",
                                  "inputs":[{"params":{"orderId":1}}],
                                  "requestContext":{"traceId":"query-trace-access"}
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("查询接口要求提供调用方标识 callerId"))
                .andExpect(jsonPath("$.traceId").value("trace-query-access"));
    }

    @Test
    void shouldRejectWhenTenantIdMissingForTenantScopedService() throws Exception {
        insertPublishedTenantScopedSimpleService("tenant-a");

        mockMvc.perform(post("/api/data-services/query")
                        .header("X-Trace-Id", "trace-query-tenant-missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_tenant",
                                  "inputs":[{"params":{"orderId":1}}],
                                  "requestContext":{"callerId":"caller-a","traceId":"query-tenant-missing"}
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("查询接口要求提供租户标识 tenantId"))
                .andExpect(jsonPath("$.traceId").value("trace-query-tenant-missing"));

        Integer deniedAuditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'QUERY_TENANT_ACCESS_DENIED' and target_id = 'svc_query_tenant'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(deniedAuditCount).isEqualTo(1);
        String latestDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'QUERY_TENANT_ACCESS_DENIED' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(latestDetail).contains("\"requiredTenantId\":\"tenant-a\"");
        org.assertj.core.api.Assertions.assertThat(latestDetail).contains("\"actualTenantId\":null");
        org.assertj.core.api.Assertions.assertThat(latestDetail).contains("\"callerId\":\"caller-a\"");
    }

    @Test
    void shouldRejectWhenTenantIdMismatchedForTenantScopedService() throws Exception {
        insertPublishedTenantScopedSimpleService("tenant-a");

        mockMvc.perform(post("/api/data-services/query")
                        .header("X-Trace-Id", "trace-query-tenant-mismatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_tenant",
                                  "inputs":[{"params":{"orderId":1}}],
                                  "requestContext":{"tenantId":"tenant-b","callerId":"caller-a","traceId":"query-tenant-mismatch"}
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("查询接口租户越权: requiredTenantId=tenant-a, actualTenantId=tenant-b"))
                .andExpect(jsonPath("$.traceId").value("trace-query-tenant-mismatch"));

        Integer deniedAuditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'QUERY_TENANT_ACCESS_DENIED' and target_id = 'svc_query_tenant'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(deniedAuditCount).isEqualTo(1);
        String latestContextSummary = jdbcTemplate.queryForObject(
                "select context_summary_json from ds_audit_log where event_type = 'QUERY_TENANT_ACCESS_DENIED' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(latestContextSummary).contains("\"requiredTenantId\":\"tenant-a\"");
        org.assertj.core.api.Assertions.assertThat(latestContextSummary).contains("\"actualTenantId\":\"tenant-b\"");
        org.assertj.core.api.Assertions.assertThat(latestContextSummary).contains("\"callerId\":\"caller-a\"");
    }

    @Test
    void shouldQueryWhenTenantIdMatchesForTenantScopedService() throws Exception {
        insertPublishedTenantScopedSimpleService("tenant-a");

        mockMvc.perform(post("/api/data-services/query")
                        .header("X-Trace-Id", "trace-query-tenant-match")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_tenant",
                                  "inputs":[{"params":{"orderId":1}}],
                                  "requestContext":{"tenantId":"tenant-a","callerId":"caller-a","traceId":"query-tenant-match","contextKeys":["tenantId","callerId"]}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.requestContext.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].rows", hasSize(1)))
                .andExpect(jsonPath("$.items[0].rows[0].id").value(1))
                .andExpect(jsonPath("$.items[0].rows[0].order_name").value("order-a"));
    }

    @Test
    void shouldWriteQueryExecutionAuditForSuccess() throws Exception {
        insertPublishedSimpleService();

        mockMvc.perform(post("/api/data-services/query")
                        .header("X-Trace-Id", "trace-query-audit-success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query",
                                  "inputs":[{"params":{"orderId":1}}],
                                  "requestContext":{"callerId":"caller-a","traceId":"query-audit-success","contextKeys":["callerId"]}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'QUERY_EXECUTE' and target_id = 'svc_query' and operation_result = 'SUCCESS'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(1);

        String detailJson = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'QUERY_EXECUTE' order by id desc limit 1",
                String.class
        );
        String contextSummaryJson = jdbcTemplate.queryForObject(
                "select context_summary_json from ds_audit_log where event_type = 'QUERY_EXECUTE' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"serviceCode\":\"svc_query\"");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"serviceVersion\":1");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"queryStatus\":\"SUCCESS\"");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"cacheHit\":false");
        org.assertj.core.api.Assertions.assertThat(contextSummaryJson).contains("\"traceId\":\"query-audit-success\"");
        org.assertj.core.api.Assertions.assertThat(contextSummaryJson).contains("\"callerId\":\"caller-a\"");
    }

    @Test
    void shouldWriteQueryExecutionAuditForFailure() throws Exception {
        insertPublishedSimpleService();

        mockMvc.perform(post("/api/data-services/query")
                        .header("X-Trace-Id", "trace-query-audit-failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query",
                                  "inputs":[{"params":{"orderId":1}}],
                                  "requestContext":{"traceId":"query-audit-failure"}
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'QUERY_EXECUTE' and target_id = 'svc_query' and operation_result = 'FAILURE'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(1);

        String detailJson = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'QUERY_EXECUTE' order by id desc limit 1",
                String.class
        );
        String contextSummaryJson = jdbcTemplate.queryForObject(
                "select context_summary_json from ds_audit_log where event_type = 'QUERY_EXECUTE' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"queryStatus\":\"FAILURE\"");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"errorCode\":\"ACCESS_DENIED\"");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"errorMessage\":\"查询接口要求提供调用方标识 callerId\"");
        org.assertj.core.api.Assertions.assertThat(contextSummaryJson).contains("\"traceId\":\"query-audit-failure\"");
        org.assertj.core.api.Assertions.assertThat(contextSummaryJson).contains("\"errorCode\":\"ACCESS_DENIED\"");
    }

    @Test
    void shouldWriteQueryExecutionAuditForCacheHit() throws Exception {
        insertPublishedSimpleService();
        insertEnabledCachePolicy(1L, 300, "[\"callerId\"]");

        String body = """
                {
                  "serviceCode":"svc_query",
                  "inputs":[{"params":{"orderId":1}}],
                  "requestContext":{"callerId":"caller-audit-cache-hit","traceId":"query-audit-cache-hit","contextKeys":["callerId"]}
                }
                """;

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(false));

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(true));

        String detailJson = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'QUERY_EXECUTE' order by id desc limit 1",
                String.class
        );
        String contextSummaryJson = jdbcTemplate.queryForObject(
                "select context_summary_json from ds_audit_log where event_type = 'QUERY_EXECUTE' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"queryStatus\":\"SUCCESS\"");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"cacheHit\":true");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"itemSummaries\":[");
        org.assertj.core.api.Assertions.assertThat(contextSummaryJson).contains("\"traceId\":\"query-audit-cache-hit\"");
        org.assertj.core.api.Assertions.assertThat(contextSummaryJson).contains("\"cacheHit\":true");
    }

    @Test
    void shouldWriteQueryExecutionAuditForResourceProtectionFailure() throws Exception {
        insertPublishedSimpleTimeoutService();

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_timeout",
                                  "inputs":[{"params":{}}],
                                  "requestContext":{"callerId":"caller-a","traceId":"query-audit-timeout"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.protectionType").value("QUERY_TIMEOUT"));

        String operationResult = jdbcTemplate.queryForObject(
                "select operation_result from ds_audit_log where event_type = 'QUERY_EXECUTE' and target_id = 'svc_query_timeout' order by id desc limit 1",
                String.class
        );
        String detailJson = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'QUERY_EXECUTE' and target_id = 'svc_query_timeout' order by id desc limit 1",
                String.class
        );
        String contextSummaryJson = jdbcTemplate.queryForObject(
                "select context_summary_json from ds_audit_log where event_type = 'QUERY_EXECUTE' and target_id = 'svc_query_timeout' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(operationResult).isEqualTo("FAILURE");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"queryStatus\":\"FAILURE\"");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"errorCode\":\"INVALID_ARGUMENT\"");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"protectionType\":\"QUERY_TIMEOUT\"");
        org.assertj.core.api.Assertions.assertThat(detailJson).contains("\"failureStage\":\"TIMEOUT_GUARD\"");
        org.assertj.core.api.Assertions.assertThat(contextSummaryJson).contains("\"traceId\":\"query-audit-timeout\"");
        org.assertj.core.api.Assertions.assertThat(contextSummaryJson).contains("\"queryStatus\":\"FAILURE\"");
        org.assertj.core.api.Assertions.assertThat(contextSummaryJson).contains("\"cacheHit\":false");
    }

    @Test
    void shouldReturnParameterDiagnosticsForMissingAndExtraParams() throws Exception {
        insertPublishedSimpleService();

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query",
                                  "inputs":[
                                    {"params":{"unexpected":1}}
                                  ],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].error.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.items[0].error.message").value("查询参数校验失败"))
                .andExpect(jsonPath("$.items[0].meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.failureStage").value("PARAM_VALIDATION"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.detailCount").value(2))
                .andExpect(jsonPath("$.items[0].error.details", hasSize(2)))
                .andExpect(jsonPath("$.items[0].error.details[0].reasonCode").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.items[0].error.details[0].path").value("inputs[0].params.orderId"))
                .andExpect(jsonPath("$.items[0].error.details[1].reasonCode").value("EXTRA_PARAMETER"))
                .andExpect(jsonPath("$.items[0].error.details[1].path").value("inputs[0].params.unexpected"));
    }

    @Test
    void shouldReturnCollectionMismatchDiagnostics() throws Exception {
        insertPublishedCollectionService();

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_collection",
                                  "inputs":[
                                    {"params":{"orderIds":"1"}}
                                  ],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].error.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.items[0].meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.failureStage").value("PARAM_VALIDATION"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.detailCount").value(1))
                .andExpect(jsonPath("$.items[0].error.details", hasSize(1)))
                .andExpect(jsonPath("$.items[0].error.details[0].reasonCode").value("COLLECTION_MISMATCH"))
                .andExpect(jsonPath("$.items[0].error.details[0].path").value("inputs[0].params.orderIds"))
                .andExpect(jsonPath("$.items[0].error.details[0].collection").value(true));
    }

    @Test
    void shouldQueryPublishedFederatedService() throws Exception {
        insertPublishedFederatedService();

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_fed",
                                  "inputs":[{"params":{"orderId":1}}],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.meta.successCount").value(1))
                .andExpect(jsonPath("$.meta.failureCount").value(0))
                .andExpect(jsonPath("$.items[0].rows", hasSize(1)))
                .andExpect(jsonPath("$.items[0].rows[0].order_id").value(1))
                .andExpect(jsonPath("$.items[0].rows[0].item_sku").value("sku-a"))
                .andExpect(jsonPath("$.items[0].meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.stage").value("FEDERATED_EXECUTION"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheMissReason").value("POLICY_DISABLED"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.taskCount").value(2))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.taskSummaries", hasSize(2)));
    }

    @Test
    void shouldReturnFederatedFailureDiagnosticsWhenResidualFilterUnsupported() throws Exception {
        insertPublishedFederatedResidualFailureService();

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_fed_fail",
                                  "inputs":[{"params":{"orderId":1}}],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.meta.successCount").value(0))
                .andExpect(jsonPath("$.meta.failureCount").value(1))
                .andExpect(jsonPath("$.items[0].status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].error.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.items[0].meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.failureStage").value("FEDERATED_EXECUTION"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.detailCount").value(1))
                .andExpect(jsonPath("$.items[0].error.details", hasSize(1)))
                .andExpect(jsonPath("$.items[0].error.details[0].reasonCode").value("UNSUPPORTED_RESIDUAL_FILTER"))
                .andExpect(jsonPath("$.items[0].error.details[0].path").value("execution.localFilter"));
    }

    @Test
    void shouldUseHeaderTraceIdInSuccessResponseWhenRequestTraceIdMissing() throws Exception {
        insertPublishedSimpleService();

        mockMvc.perform(post("/api/data-services/query")
                        .header("X-Trace-Id", "trace-from-header")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query",
                                  "inputs":[{"params":{"orderId":1}}],
                                  "requestContext":{"callerId":"caller-a","contextKeys":["callerId"]}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestContext.traceId").value("trace-from-header"))
                .andExpect(jsonPath("$.meta.traceId").value("trace-from-header"));
    }

    @Test
    void shouldHitCacheWhenPolicyEnabledAndRequestRepeated() throws Exception {
        insertPublishedSimpleService();
        insertEnabledCachePolicy(1L, 300, "[\"callerId\"]");

        String body = """
                {
                  "serviceCode":"svc_query",
                  "inputs":[{"params":{"orderId":1}}],
                  "requestContext":{"callerId":"caller-a","contextKeys":["callerId"]}
                }
                """;

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheMissReason").value("MISS"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheWriteSkippedReason").doesNotExist())
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.contextDigest").isString());

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(true))
                .andExpect(jsonPath("$.items[0].meta.cacheHit").value(true))
                .andExpect(jsonPath("$.items[0].meta.elapsedMs").value(0))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheHit").value(true))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheMissReason").doesNotExist());
    }

    @Test
    void shouldIsolateCacheAcrossDifferentContextValues() throws Exception {
        insertPublishedSimpleService();
        insertEnabledCachePolicy(1L, 300, "[\"callerId\"]");

        String callerABody = """
                {
                  "serviceCode":"svc_query",
                  "inputs":[{"params":{"orderId":1}}],
                  "requestContext":{"callerId":"caller-a","contextKeys":["callerId"]}
                }
                """;
        String callerBBody = """
                {
                  "serviceCode":"svc_query",
                  "inputs":[{"params":{"orderId":1}}],
                  "requestContext":{"callerId":"caller-b","contextKeys":["callerId"]}
                }
                """;

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callerABody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(false));

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callerABody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(true));

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callerBBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheMissReason").value("MISS"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheIsolationReason").value("CONTEXT_KEYS_DECLARED:callerId"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.contextDigest").isString());
    }

    @Test
    void shouldMissCacheAfterPolicyChange() throws Exception {
        insertPublishedSimpleService();
        insertEnabledCachePolicy(1L, 300, "[\"callerId\"]");

        String body = """
                {
                  "serviceCode":"svc_query",
                  "inputs":[{"params":{"orderId":1}}],
                  "requestContext":{"callerId":"caller-a","contextKeys":["callerId"]}
                }
                """;

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(true));

        mockMvc.perform(put("/api/admin/services/1/cache-policy")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled":true,
                                  "ttlSeconds":300,
                                  "cacheKeyTemplate":"data-service:{serviceCode}:v{version}:{paramHash}:{contextDigest}",
                                  "maxEntries":1000,
                                  "contextKeys":["tenantId"],
                                  "remark":"cache policy updated"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheMissReason").value("MISS"));
    }

    @Test
    void shouldMissCacheAfterTtlExpires() throws Exception {
        insertPublishedSimpleService();
        insertEnabledCachePolicy(1L, 1, "[\"callerId\"]");

        String body = """
                {
                  "serviceCode":"svc_query",
                  "inputs":[{"params":{"orderId":1}}],
                  "requestContext":{"callerId":"caller-a","contextKeys":["callerId"]}
                }
                """;

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(false));

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(true));

        Thread.sleep(1200L);

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheMissReason").value("TTL_EXPIRED"));
    }

    @Test
    void shouldMissCacheAfterPublishingNewVersion() throws Exception {
        insertRepublishableSimpleService();
        insertEnabledCachePolicy(20L, 300, "[\"callerId\"]");

        String body = """
                {
                  "serviceCode":"svc_query_republish",
                  "inputs":[{"params":{"orderId":1}}],
                  "requestContext":{"callerId":"caller-a","contextKeys":["callerId"]}
                }
                """;

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceVersion").value(1))
                .andExpect(jsonPath("$.meta.cacheHit").value(false));

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceVersion").value(1))
                .andExpect(jsonPath("$.meta.cacheHit").value(true));

        mockMvc.perform(post("/api/admin/services/20/publish")
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
                .andExpect(jsonPath("$.currentVersion").value(2));

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceVersion").value(2))
                .andExpect(jsonPath("$.meta.cacheHit").value(false))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheNamespaceVersion").value(2))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.cacheMissReason").value("MISS"));
    }

    @Test
    void shouldRejectQueryAfterServiceDisabledEvenWhenCachePrimed() throws Exception {
        insertPublishedSimpleService();
        insertEnabledCachePolicy(1L, 300, "[\"callerId\"]");

        String body = """
                {
                  "serviceCode":"svc_query",
                  "inputs":[{"params":{"orderId":1}}],
                  "requestContext":{"callerId":"caller-a","contextKeys":["callerId"]}
                }
                """;

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.cacheHit").value(true));

        mockMvc.perform(put("/api/admin/services/1/status")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status":"DISABLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(post("/api/data-services/query")
                        .header("X-Trace-Id", "trace-disabled-cache")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("服务未发布: svc_query"))
                .andExpect(jsonPath("$.traceId").value("trace-disabled-cache"));
    }

    @Test
    void shouldFailWhenSingleSourceResultExceedsMaxRows() throws Exception {
        insertPublishedSimpleServiceWithMaxRows(1);

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_limit",
                                  "inputs":[{"params":{}}],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].error.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.items[0].error.message").value("结果集超过服务上限: stage=SINGLE_SOURCE_EXECUTION, maxResultRows=1, actualRows=2"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.failureStage").value("RESULT_GUARD"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.protectionType").value("RESULT_ROWS"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.guardStage").value("SINGLE_SOURCE_EXECUTION"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.maxResultRows").value(1))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.actualRows").value(2))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.detailCount").value(0));
    }

    @Test
    void shouldFailWhenFederatedResultExceedsMaxRows() throws Exception {
        insertPublishedFederatedServiceWithMaxRows(1);

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_fed_limit",
                                  "inputs":[{"params":{}}],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].error.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.items[0].error.message").value("结果集超过服务上限: stage=FEDERATED_EXECUTION, maxResultRows=1, actualRows=2"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.failureStage").value("RESULT_GUARD"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.protectionType").value("RESULT_ROWS"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.guardStage").value("FEDERATED_EXECUTION"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.maxResultRows").value(1))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.actualRows").value(2))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.detailCount").value(0));
    }

    @Test
    void shouldRejectWhenBatchSizeExceedsServiceLimit() throws Exception {
        insertPublishedSimpleServiceWithBatchAndRows(12L, "svc_query_batch_limit", 1, 100);

        mockMvc.perform(post("/api/data-services/query")
                        .header("X-Trace-Id", "trace-batch-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_batch_limit",
                                  "inputs":[
                                    {"params":{"orderId":1}},
                                    {"params":{"orderId":2}}
                                  ],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("批量请求超过服务上限: maxBatchSize=1, actualBatchSize=2"))
                .andExpect(jsonPath("$.traceId").value("trace-batch-limit"))
                .andExpect(jsonPath("$.diagnosticSummary.failureStage").value("BATCH_GUARD"))
                .andExpect(jsonPath("$.diagnosticSummary.protectionType").value("BATCH_SIZE"))
                .andExpect(jsonPath("$.diagnosticSummary.maxBatchSize").value(1))
                .andExpect(jsonPath("$.diagnosticSummary.actualBatchSize").value(2));
    }

    @Test
    void shouldFailWhenSingleSourceQueryTimeoutExceeded() throws Exception {
        insertPublishedSimpleTimeoutService();

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_timeout",
                                  "inputs":[{"params":{}}],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].error.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.items[0].error.message").value("查询执行超时: stage=SINGLE_SOURCE_EXECUTION, timeoutSeconds=1"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.failureStage").value("TIMEOUT_GUARD"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.protectionType").value("QUERY_TIMEOUT"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.guardStage").value("SINGLE_SOURCE_EXECUTION"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.timeoutSeconds").value(1))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.detailCount").value(0));
    }

    @Test
    void shouldFailWhenSingleSourceTableMissing() throws Exception {
        insertPublishedMissingTableService();

        mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_query_missing_table",
                                  "inputs":[{"params":{"orderId":1}}],
                                  "requestContext":{"callerId":"caller-a"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].status").value("FAILURE"))
                .andExpect(jsonPath("$.items[0].error.errorCode").value("DATASOURCE_CONNECTION_TEST_FAILED"))
                .andExpect(jsonPath("$.items[0].error.message", containsString("missing_orders")))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.failureStage").value("QUERY_SERVICE"))
                .andExpect(jsonPath("$.items[0].meta.diagnosticSummary.detailCount").value(0));
    }

    private void insertPublishedSimpleService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_query', 'query service', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    1, 1, 1, 'PUBLISHED', 'SIMPLE_SQL',
                    'select id, order_name from public.orders where id = /* orderId */1',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]',
                    '[{"fieldName":"id","expression":"id","sortOrder":1},{"fieldName":"order_name","expression":"order_name","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"SIMPLE_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
    }

    private void insertPublishedTenantScopedSimpleService(String tenantId) {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    tenant_id, deleted, created_by, updated_by
                ) values (
                    15, 'svc_query_tenant', 'query tenant service', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', 10, 100, 30, 60,
                    ?, false, 'tester', 'tester'
                )
                """, tenantId);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    15, 15, 1, 'PUBLISHED', 'SIMPLE_SQL',
                    'select id, order_name from public.orders where id = /* orderId */1',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]',
                    '[{"fieldName":"id","expression":"id","sortOrder":1},{"fieldName":"order_name","expression":"order_name","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"SIMPLE_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
    }

    private void insertPublishedCollectionService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    2, 'svc_query_collection', 'query collection service', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    2, 2, 1, 'PUBLISHED', 'SIMPLE_SQL',
                    'select id, order_name from public.orders where id in /* orderIds */(1,2)',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[{"paramName":"orderIds","paramType":"LONG","placeholder":"/* orderIds */","collection":true,"defaultValue":"(1,2)"}]',
                    '[{"fieldName":"id","expression":"id","sortOrder":1},{"fieldName":"order_name","expression":"order_name","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"SIMPLE_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
    }

    private void insertPublishedSimpleServiceWithMaxRows(int maxRows) {
        insertPublishedSimpleServiceWithBatchAndRows(11L, "svc_query_limit", 10, maxRows);
    }

    private void insertPublishedFederatedServiceWithMaxRows(int maxRows) {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    13, 'svc_query_fed_limit', 'query federated service limit', 'FEDERATED_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', 10, ?, 30, 60,
                    false, 'tester', 'tester'
                )
                """, maxRows);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    13, 13, 1, 'PUBLISHED', 'FEDERATED_SQL',
                    'select o.id as order_id, oi.sku as item_sku from PG_MAIN@public.orders o join PG_ARCHIVE@public.order_items oi on o.id = oi.order_id',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"},{"connectionCode":"PG_ARCHIVE","schemaName":"public","databaseName":"data_service","tableName":"order_items","alias":"oi","dbType":"POSTGRESQL"}]',
                    '[]',
                    '[{"fieldName":"order_id","expression":"o.id","sortOrder":1},{"fieldName":"item_sku","expression":"oi.sku","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"FEDERATED_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
    }

    private void insertPublishedSimpleTimeoutService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    14, 'svc_query_timeout', 'query service timeout', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', 10, 100, 1, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    14, 14, 1, 'PUBLISHED', 'SIMPLE_SQL',
                    'select count(*) as total_count from generate_series(1,50000000)',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[]',
                    '[{"fieldName":"total_count","expression":"count(*)","sortOrder":1}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"SIMPLE_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
    }

    private void insertPublishedMissingTableService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    16, 'svc_query_missing_table', 'query service missing table', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    16, 16, 1, 'PUBLISHED', 'SIMPLE_SQL',
                    'select id, order_name from public.missing_orders where id = /* orderId */1',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"missing_orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]',
                    '[{"fieldName":"id","expression":"id","sortOrder":1},{"fieldName":"order_name","expression":"order_name","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"SIMPLE_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
    }

    private void insertRepublishableSimpleService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    20, 'svc_query_republish', 'query republish service', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    20, 20, 1, 'PUBLISHED', 'SIMPLE_SQL',
                    'select id, order_name from public.orders where id = /* orderId */1',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]',
                    '[{"fieldName":"id","expression":"id","sortOrder":1},{"fieldName":"order_name","expression":"order_name","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"SIMPLE_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    created_by, updated_by
                ) values (
                    21, 20, 2, 'DRAFT', 'SIMPLE_SQL',
                    'select id, order_name from public.orders where id = /* orderId */1',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]',
                    '[{"fieldName":"id","expression":"id","sortOrder":1},{"fieldName":"order_name","expression":"order_name","sortOrder":2}]',
                    '{"stage":"SAVE","result":"PASS"}',
                    null,
                    'tester', 'tester'
                )
                """);
    }

    private void insertPublishedFederatedService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    3, 'svc_query_fed', 'query federated service', 'FEDERATED_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    3, 3, 1, 'PUBLISHED', 'FEDERATED_SQL',
                    'select o.id as order_id, oi.sku as item_sku from PG_MAIN@public.orders o join PG_ARCHIVE@public.order_items oi on o.id = oi.order_id where o.id = /* orderId */1',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"},{"connectionCode":"PG_ARCHIVE","schemaName":"public","databaseName":"data_service","tableName":"order_items","alias":"oi","dbType":"POSTGRESQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]',
                    '[{"fieldName":"order_id","expression":"o.id","sortOrder":1},{"fieldName":"item_sku","expression":"oi.sku","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"FEDERATED_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
    }

    private void insertPublishedPostgresMysqlFederatedService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    18, 'svc_query_pg_mysql_fed', 'query postgres mysql federated service', 'FEDERATED_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    18, 18, 1, 'PUBLISHED', 'FEDERATED_SQL',
                    'select o.id as order_id, m.product_name as product_name from PG_MAIN@public.orders o join MY_MAIN@mysql_federation1.fed_test_order m on o.id = m.order_id where m.order_id = /* orderId */20001',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"},{"connectionCode":"MY_MAIN","schemaName":"mysql_federation1","databaseName":"mysql_federation1","tableName":"fed_test_order","alias":"m","dbType":"MYSQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"20001"}]',
                    '[{"fieldName":"order_id","expression":"o.id","sortOrder":1},{"fieldName":"product_name","expression":"m.product_name","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"FEDERATED_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
    }

    private void insertPublishedOracleService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    19, 'svc_query_oracle', 'query oracle service', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    'ORACLE_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    19, 19, 1, 'PUBLISHED', 'SIMPLE_SQL',
                    'select id, order_name from ORACLE_TEST.orders where id = /* orderId */20001',
                    '[{"connectionCode":"ORACLE_MAIN","schemaName":"ORACLE_TEST","databaseName":"xepdb1","tableName":"orders","alias":null,"dbType":"ORACLE"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"20001"}]',
                    '[{"fieldName":"id","expression":"id","sortOrder":1},{"fieldName":"order_name","expression":"order_name","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"SIMPLE_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
    }

    private void insertPublishedMysqlService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    17, 'svc_query_mysql', 'query mysql service', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    'MY_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    17, 17, 1, 'PUBLISHED', 'SIMPLE_SQL',
                    'select order_id, product_name from mysql_federation1.fed_test_order where order_id = /* orderId */20001',
                    '[{"connectionCode":"MY_MAIN","schemaName":"mysql_federation1","databaseName":"mysql_federation1","tableName":"fed_test_order","alias":null,"dbType":"MYSQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"20001"}]',
                    '[{"fieldName":"order_id","expression":"order_id","sortOrder":1},{"fieldName":"product_name","expression":"product_name","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"SIMPLE_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
    }

    private void insertPublishedFederatedResidualFailureService() {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    4, 'svc_query_fed_fail', 'query federated fail service', 'FEDERATED_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', 10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    4, 4, 1, 'PUBLISHED', 'FEDERATED_SQL',
                    'select o.id as order_id, oi.sku as item_sku from PG_MAIN@public.orders o join PG_ARCHIVE@public.order_items oi on o.id = oi.order_id where o.id = /* orderId */1 or oi.order_id = /* orderId */1',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"},{"connectionCode":"PG_ARCHIVE","schemaName":"public","databaseName":"data_service","tableName":"order_items","alias":"oi","dbType":"POSTGRESQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]',
                    '[{"fieldName":"order_id","expression":"o.id","sortOrder":1},{"fieldName":"item_sku","expression":"oi.sku","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"FEDERATED_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
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

    private void insertEnabledMysqlConnection(String connectionCode) {
        jdbcTemplate.update("""
                insert into ds_connection (
                    id, connection_code, connection_name, db_type, host, port, username,
                    password_ciphertext, status, remark, connection_config_json, deleted, created_by, updated_by
                ) values (
                    nextval('ds_connection_id_seq'), ?, ?, 'MYSQL', '127.0.0.1', 3306, ?,
                    ?, 'ENABLED', 'mysql test connection', '{"databaseName":"mysql_federation1"}', false, 'tester', 'tester'
                )
                """,
                connectionCode,
                connectionCode,
                System.getenv("MYSQL_USER"),
                credentialCodec.encrypt(System.getenv("MYSQL_PASSWORD"))
        );
    }

    private void insertEnabledOracleConnection(String connectionCode) {
        jdbcTemplate.update("""
                insert into ds_connection (
                    id, connection_code, connection_name, db_type, host, port, username,
                    password_ciphertext, status, remark, connection_config_json, deleted, created_by, updated_by
                ) values (
                    nextval('ds_connection_id_seq'), ?, ?, 'ORACLE', '127.0.0.1', 1521, 'ORACLE_TEST',
                    ?, 'ENABLED', 'oracle test connection', '{"databaseName":"xepdb1"}', false, 'tester', 'tester'
                )
                """,
                connectionCode,
                connectionCode,
                credentialCodec.encrypt("oracle")
        );
    }

    private boolean hasMysqlTestCredentials() {
        String user = System.getenv("MYSQL_USER");
        String password = System.getenv("MYSQL_PASSWORD");
        return user != null && !user.isBlank() && password != null && !password.isBlank();
    }

    private boolean hasOracleTestEnvironment() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 1521), 1000);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private void insertPublishedSimpleServiceWithBatchAndRows(long id,
                                                              String serviceCode,
                                                              int maxBatchSize,
                                                              int maxResultRows) {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    ?, ?, 'query service limit', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    'PG_MAIN', ?, ?, 30, 60,
                    false, 'tester', 'tester'
                )
                """, id, serviceCode, maxBatchSize, maxResultRows);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, plan_snapshot_json,
                    published_at, published_by, created_by, updated_by
                ) values (
                    ?, ?, 1, 'PUBLISHED', 'SIMPLE_SQL',
                    'select id, order_name from public.orders order by id',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[]',
                    '[{"fieldName":"id","expression":"id","sortOrder":1},{"fieldName":"order_name","expression":"order_name","sortOrder":2}]',
                    '{"stage":"PUBLISH","result":"PASS"}',
                    '{"stage":"PUBLISH","sqlType":"SIMPLE_SQL"}',
                    current_timestamp, 'tester', 'tester', 'tester'
                )
                """, id, id);
    }

    private void insertEnabledCachePolicy(Long serviceId, int ttlSeconds, String contextKeysJson) {
        jdbcTemplate.update("""
                insert into ds_cache_policy (
                    service_id, enabled, ttl_seconds, cache_key_template, max_entries,
                    context_keys_json, remark, deleted, created_by, updated_by
                ) values (
                    ?, true, ?, 'data-service:{serviceCode}:v{version}:{paramHash}:{contextDigest}', 1000,
                    ?, 'query cache policy', false, 'tester', 'tester'
                )
                """,
                serviceId,
                ttlSeconds,
                contextKeysJson
        );
    }
}
