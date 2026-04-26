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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DataServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminServiceDefinitionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CredentialCodec credentialCodec;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("delete from ds_audit_log");
        jdbcTemplate.execute("""
                delete from ds_dialect_rule
                where not (
                    connection_id is null
                    and db_type in ('POSTGRESQL', 'MYSQL', 'ORACLE')
                    and rule_code in ('STRING_CONCAT', 'CURRENT_TIMESTAMP')
                )
                """);
        jdbcTemplate.execute("delete from ds_source_capability");
        jdbcTemplate.execute("delete from ds_cache_policy");
        jdbcTemplate.execute("delete from ds_service_version");
        jdbcTemplate.execute("delete from ds_service");
        jdbcTemplate.execute("delete from ds_connection");
        jdbcTemplate.execute("drop table if exists public.order_items");
        jdbcTemplate.execute("drop table if exists public.orders");
        jdbcTemplate.execute("create table public.orders (id bigint primary key, order_name varchar(64))");
        jdbcTemplate.execute("create table public.order_items (id bigint primary key, order_id bigint)");
        insertEnabledConnection("PG_MAIN");
    }

    @Test
    void shouldCreateListAndDetailServiceDraft() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-service-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_orders",
                                  "serviceName":"Orders Query",
                                  "sqlType":"FEDERATED_SQL",
                                  "defaultConnectionCode":"",
                                  "tenantId":"tenant-a",
                                  "sqlText":"select * from PG_MAIN@public.orders o",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60,
                                  "remark":"draft service"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceCode").value("svc_orders"))
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.currentVersion").doesNotExist())
                .andExpect(jsonPath("$.draftVersion.version").value(1))
                .andExpect(jsonPath("$.draftVersion.sqlText").value("select * from PG_MAIN@public.orders o"))
                .andExpect(jsonPath("$.draftVersion.validationSnapshotJson").value(org.hamcrest.Matchers.containsString("\"stage\":\"SAVE\"")))
                .andExpect(jsonPath("$.draftVersion.validationSnapshotJson").value(org.hamcrest.Matchers.containsString("\"result\":\"PASS\"")))
                .andExpect(jsonPath("$.versionHistory", hasSize(1)))
                .andExpect(jsonPath("$.versionHistory[0].sourceSnapshots", hasSize(1)))
                .andExpect(jsonPath("$.versionHistory[0].validationSnapshotJson").value(org.hamcrest.Matchers.containsString("\"stage\":\"SAVE\"")))
                .andExpect(jsonPath("$.versionHistory[0].sourceSnapshots[0].connectionCode").value("PG_MAIN"))
                .andExpect(jsonPath("$.versionHistory[0].sourceSnapshots[0].tableName").value("orders"))
                .andExpect(jsonPath("$.versionHistory[0].sourceSnapshots[0].alias").value("o"));

        Long serviceId = jdbcTemplate.queryForObject(
                "select id from ds_service where service_code = 'svc_orders'",
                Long.class
        );

        mockMvc.perform(get("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tenantId").value("tenant-a"))
                .andExpect(jsonPath("$[0].draftVersion.version").value(1));

        mockMvc.perform(get("/api/admin/services/" + serviceId)
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("Orders Query"))
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.draftVersion.status").value("DRAFT"))
                .andExpect(jsonPath("$.draftVersion.validationSnapshotJson").value(org.hamcrest.Matchers.containsString("\"message\":\"保存校验通过\"")));

        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'CREATE_SERVICE'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void shouldPersistCompleteDraftSnapshotsAndAuditWhenSaveSucceeds() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-save-success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_save_success",
                                  "serviceName":"Save Success",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select o.id as order_id, concat(o.order_name, '-x') as order_label from public.orders o where o.id = /* orderId */1",
                                  "paramDefinitions":[
                                    {"paramName":"orderId","paramType":"LONG"}
                                  ],
                                  "maxBatchSize":10,
                                  "maxResultRows":100,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60,
                                  "remark":"save-success"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceCode").value("svc_save_success"))
                .andExpect(jsonPath("$.draftVersion.version").value(1))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots", hasSize(1)))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots[0].connectionCode").value("PG_MAIN"))
                .andExpect(jsonPath("$.draftVersion.paramSnapshotJson").value(org.hamcrest.Matchers.containsString("\"paramName\":\"orderId\"")))
                .andExpect(jsonPath("$.draftVersion.paramSnapshotJson").value(org.hamcrest.Matchers.containsString("\"paramType\":\"LONG\"")))
                .andExpect(jsonPath("$.draftVersion.fieldSnapshotJson").value(org.hamcrest.Matchers.containsString("\"fieldName\":\"order_id\"")))
                .andExpect(jsonPath("$.draftVersion.fieldSnapshotJson").value(org.hamcrest.Matchers.containsString("\"fieldName\":\"order_label\"")))
                .andExpect(jsonPath("$.draftVersion.validationSnapshotJson").value(org.hamcrest.Matchers.containsString("\"stage\":\"SAVE\"")))
                .andExpect(jsonPath("$.draftVersion.validationSnapshotJson").value(org.hamcrest.Matchers.containsString("\"PARAM_TYPES_CONFIRMED\"")))
                .andExpect(jsonPath("$.draftVersion.validationSnapshotJson").value(org.hamcrest.Matchers.containsString("\"EXPRESSION_SUPPORT\"")))
                .andExpect(jsonPath("$.draftVersion.planSnapshotJson").doesNotExist());

        Long serviceId = jdbcTemplate.queryForObject(
                "select id from ds_service where service_code = 'svc_save_success'",
                Long.class
        );
        String sourceSnapshotJson = jdbcTemplate.queryForObject(
                "select source_snapshot_json from ds_service_version where service_id = ? and version = 1",
                String.class,
                serviceId
        );
        String paramSnapshotJson = jdbcTemplate.queryForObject(
                "select param_snapshot_json from ds_service_version where service_id = ? and version = 1",
                String.class,
                serviceId
        );
        String fieldSnapshotJson = jdbcTemplate.queryForObject(
                "select field_snapshot_json from ds_service_version where service_id = ? and version = 1",
                String.class,
                serviceId
        );
        String validationSnapshotJson = jdbcTemplate.queryForObject(
                "select validation_snapshot_json from ds_service_version where service_id = ? and version = 1",
                String.class,
                serviceId
        );
        String auditDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'CREATE_SERVICE' and target_id = 'svc_save_success' order by id desc limit 1",
                String.class
        );

        org.assertj.core.api.Assertions.assertThat(sourceSnapshotJson).contains("\"connectionCode\":\"PG_MAIN\"");
        org.assertj.core.api.Assertions.assertThat(sourceSnapshotJson).contains("\"tableName\":\"orders\"");
        org.assertj.core.api.Assertions.assertThat(paramSnapshotJson).contains("\"paramName\":\"orderId\"");
        org.assertj.core.api.Assertions.assertThat(paramSnapshotJson).contains("\"paramType\":\"LONG\"");
        org.assertj.core.api.Assertions.assertThat(fieldSnapshotJson).contains("\"fieldName\":\"order_id\"");
        org.assertj.core.api.Assertions.assertThat(fieldSnapshotJson).contains("\"fieldName\":\"order_label\"");
        org.assertj.core.api.Assertions.assertThat(validationSnapshotJson).contains("\"message\":\"保存校验通过\"");
        org.assertj.core.api.Assertions.assertThat(validationSnapshotJson).contains("\"checkedRules\":[");
        org.assertj.core.api.Assertions.assertThat(auditDetail).contains("\"serviceCode\":\"svc_save_success\"");
        org.assertj.core.api.Assertions.assertThat(auditDetail).contains("\"version\":1");
    }

    @Test
    void shouldGenerateParamSnapshotAndRejectOrdinaryBlockComment() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_param_snapshot",
                                  "serviceName":"Param Snapshot",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select * from public.orders where id = /* orderId */1 and id in /* orderIds */(1,2,3)",
                                  "paramDefinitions":[
                                    {"paramName":"orderId","paramType":"LONG"},
                                    {"paramName":"orderIds","paramType":"LONG"}
                                  ],
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draftVersion.paramSnapshotJson").value(org.hamcrest.Matchers.containsString("\"paramName\":\"orderId\"")))
                .andExpect(jsonPath("$.draftVersion.paramSnapshotJson").value(org.hamcrest.Matchers.containsString("\"paramName\":\"orderIds\"")))
                .andExpect(jsonPath("$.draftVersion.paramSnapshotJson").value(org.hamcrest.Matchers.containsString("\"paramType\":\"LONG\"")))
                .andExpect(jsonPath("$.draftVersion.paramSnapshotJson").value(org.hamcrest.Matchers.containsString("\"collection\":true")));

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_plain_comment",
                                  "serviceName":"Plain Comment",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select * from public.orders /* comment */ where id = 1",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("不允许普通块注释，请使用 Doma 参数或条件块语法"));
    }

    @Test
    void shouldRejectUnsupportedExpressionDuringSave() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_expr_unsupported",
                                  "serviceName":"Expr Unsupported",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select cast(o.id as json) as payload_json from public.orders o",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("复杂表达式不受支持")));
    }

    @Test
    void shouldRejectSaveWhenParamTypeNotConfirmedOrParamDefinitionRedundant() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-save-param-missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_param_type_missing",
                                  "serviceName":"Param Type Missing",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select * from public.orders where id = /* orderId */1",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("SQL 参数类型未确认: orderId"));

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-save-param-extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_param_type_extra",
                                  "serviceName":"Param Type Extra",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select * from public.orders where id = /* orderId */1",
                                  "paramDefinitions":[
                                    {"paramName":"orderId","paramType":"LONG"},
                                    {"paramName":"extraParam","paramType":"STRING"}
                                  ],
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("参数类型确认包含未使用的 SQL 参数: extraParam"));

        Integer failureAuditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'CREATE_SERVICE' and operation_result = 'FAILURE' and target_id in ('svc_param_type_missing', 'svc_param_type_extra')",
                Integer.class
        );
        String missingTypeAuditDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'CREATE_SERVICE' and target_id = 'svc_param_type_missing' order by id desc limit 1",
                String.class
        );
        String extraTypeAuditDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'CREATE_SERVICE' and target_id = 'svc_param_type_extra' order by id desc limit 1",
                String.class
        );
        Integer failedServiceCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_service where service_code in ('svc_param_type_missing', 'svc_param_type_extra')",
                Integer.class
        );

        org.assertj.core.api.Assertions.assertThat(failureAuditCount).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(missingTypeAuditDetail).contains("\"serviceCode\":\"svc_param_type_missing\"");
        org.assertj.core.api.Assertions.assertThat(missingTypeAuditDetail).contains("\"errorMessage\":\"SQL 参数类型未确认: orderId\"");
        org.assertj.core.api.Assertions.assertThat(extraTypeAuditDetail).contains("\"serviceCode\":\"svc_param_type_extra\"");
        org.assertj.core.api.Assertions.assertThat(extraTypeAuditDetail).contains("\"errorMessage\":\"参数类型确认包含未使用的 SQL 参数: extraParam\"");
        org.assertj.core.api.Assertions.assertThat(failedServiceCount).isZero();
    }

    @Test
    void shouldBindDefaultConnectionAndGenerateSimpleSqlSourceSnapshot() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_simple_source",
                                  "serviceName":"Simple Source",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select * from public.orders o join public.order_items oi on o.id = oi.order_id",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultConnectionCode").value("PG_MAIN"))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"connectionCode\":\"PG_MAIN\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"schemaName\":\"public\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"databaseName\":\"data_service\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"tableName\":\"orders\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"alias\":\"o\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"tableName\":\"order_items\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"alias\":\"oi\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"dbType\":\"POSTGRESQL\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots", hasSize(2)))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots[0].connectionCode").value("PG_MAIN"))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots[0].schemaName").value("public"))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots[0].tableName").value("orders"))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots[0].alias").value("o"))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots[1].connectionCode").value("PG_MAIN"))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots[1].tableName").value("order_items"))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots[1].alias").value("oi"));
    }

    @Test
    void shouldGenerateFederatedSourceSnapshot() throws Exception {
        insertEnabledConnection("PG_ARCHIVE");

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_federated_source",
                                  "serviceName":"Federated Source",
                                  "sqlType":"FEDERATED_SQL",
                                  "sqlText":"select * from PG_MAIN@public.orders o join PG_ARCHIVE@public.order_items oi on o.id = oi.order_id",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"connectionCode\":\"PG_MAIN\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"connectionCode\":\"PG_ARCHIVE\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"tableName\":\"orders\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"tableName\":\"order_items\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"alias\":\"o\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"alias\":\"oi\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots", hasSize(2)))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots[0].connectionCode").value("PG_MAIN"))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshots[1].connectionCode").value("PG_ARCHIVE"));
    }

    @Test
    void shouldRejectFederatedMultiTableWithoutAlias() throws Exception {
        insertEnabledConnection("PG_ARCHIVE");

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_federated_alias_missing",
                                  "serviceName":"Federated Alias Missing",
                                  "sqlType":"FEDERATED_SQL",
                                  "sqlText":"select * from PG_MAIN@public.orders join PG_ARCHIVE@public.order_items on orders.id = order_items.order_id",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("联邦 SQL 多表查询必须显式指定表别名"));
    }

    @Test
    void shouldRecursivelyParseSourcesInCteAndSubquery() throws Exception {
        insertEnabledConnection("PG_ARCHIVE");

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_recursive_sources",
                                  "serviceName":"Recursive Sources",
                                  "sqlType":"FEDERATED_SQL",
                                  "sqlText":"with filtered_orders as (select o.id from PG_MAIN@public.orders o where exists (select 1 from PG_ARCHIVE@public.order_items oi where oi.order_id = o.id)) select filtered_orders.id from filtered_orders",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"connectionCode\":\"PG_MAIN\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"tableName\":\"orders\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"alias\":\"o\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"connectionCode\":\"PG_ARCHIVE\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"tableName\":\"order_items\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"alias\":\"oi\"")));
    }

    @Test
    void shouldRejectWhenNestedCteReferencesMissingTable() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_recursive_missing_table",
                                  "serviceName":"Recursive Missing Table",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"with filtered_orders as (select o.id from public.orders o where exists (select 1 from public.missing_order_items oi where oi.order_id = o.id)) select filtered_orders.id from filtered_orders",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("SQL 引用的 table 不存在: connection=PG_MAIN, schema=public, table=missing_order_items"));
    }

    @Test
    void shouldParseSourcesAcrossUnionBranches() throws Exception {
        insertEnabledConnection("PG_ARCHIVE");

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_union_sources",
                                  "serviceName":"Union Sources",
                                  "sqlType":"FEDERATED_SQL",
                                  "sqlText":"select o.id as record_id from PG_MAIN@public.orders o union all select oi.order_id as record_id from PG_ARCHIVE@public.order_items oi",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"connectionCode\":\"PG_MAIN\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"tableName\":\"orders\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"alias\":\"o\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"connectionCode\":\"PG_ARCHIVE\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"tableName\":\"order_items\"")))
                .andExpect(jsonPath("$.draftVersion.sourceSnapshotJson").value(org.hamcrest.Matchers.containsString("\"alias\":\"oi\"")));
    }

    @Test
    void shouldRejectWhenUnionBranchReferencesMissingTable() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_union_missing_table",
                                  "serviceName":"Union Missing Table",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select o.id as record_id from public.orders o union all select oi.order_id as record_id from public.missing_order_items oi",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("SQL 引用的 table 不存在: connection=PG_MAIN, schema=public, table=missing_order_items"));
    }

    @Test
    void shouldGenerateFieldSnapshotAndRejectExpressionWithoutAlias() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_field_snapshot",
                                  "serviceName":"Field Snapshot",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select o.id, count(*) as total_count, o.* from public.orders o",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draftVersion.fieldSnapshotJson").value(org.hamcrest.Matchers.containsString("\"fieldName\":\"id\"")))
                .andExpect(jsonPath("$.draftVersion.fieldSnapshotJson").value(org.hamcrest.Matchers.containsString("\"expression\":\"o.id\"")))
                .andExpect(jsonPath("$.draftVersion.fieldSnapshotJson").value(org.hamcrest.Matchers.containsString("\"fieldName\":\"total_count\"")))
                .andExpect(jsonPath("$.draftVersion.fieldSnapshotJson").value(org.hamcrest.Matchers.containsString("\"expression\":\"count(*)\"")))
                .andExpect(jsonPath("$.draftVersion.fieldSnapshotJson").value(org.hamcrest.Matchers.containsString("\"fieldName\":\"o.*\"")));

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_field_alias_missing",
                                  "serviceName":"Field Alias Missing",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select count(*) from public.orders",
                                  "maxBatchSize":100,
                                  "maxResultRows":500,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("复杂表达式输出列必须显式使用 as 别名"));
    }

    @Test
    void shouldUpdateDraftWithoutCreatingNewVersionWhenDraftExists() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_edit', 'before', 'SIMPLE_SQL', 'DRAFT', null,
                    10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'SIMPLE_SQL', 'select 1', '[]',
                    '[]', '[]', 'tester', 'tester'
                )
                """);

        mockMvc.perform(put("/api/admin/services/1")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-update-guard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName":"after",
                                  "sqlType":"FEDERATED_SQL",
                                  "tenantId":"tenant-b",
                                  "sqlText":"select * from PG_MAIN@public.orders",
                                  "maxBatchSize":20,
                                  "maxResultRows":200,
                                  "queryTimeoutSeconds":40,
                                  "federatedQueryTimeoutSeconds":80,
                                  "remark":"updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("after"))
                .andExpect(jsonPath("$.tenantId").value("tenant-b"))
                .andExpect(jsonPath("$.draftVersion.version").value(1))
                .andExpect(jsonPath("$.draftVersion.sqlType").value("FEDERATED_SQL"))
                .andExpect(jsonPath("$.versionHistory", hasSize(1)));

        Integer versionCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_service_version where service_id = 1",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(versionCount).isEqualTo(1);
    }

    @Test
    void shouldCreateNewDraftVersionWhenUpdatingPublishedService() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_published', 'published service', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, published_at, published_by, created_by, updated_by
                ) values (
                    1, 1, 1, 'PUBLISHED', 'SIMPLE_SQL', 'select 1', '[]',
                    '[]', '[]', current_timestamp, 'tester', 'tester', 'tester'
                )
                """);

        mockMvc.perform(put("/api/admin/services/1")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName":"published service updated",
                                  "sqlType":"FEDERATED_SQL",
                                  "tenantId":"tenant-c",
                                  "sqlText":"select * from PG_MAIN@public.orders",
                                  "maxBatchSize":50,
                                  "maxResultRows":300,
                                  "queryTimeoutSeconds":45,
                                  "federatedQueryTimeoutSeconds":90,
                                  "remark":"new draft"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.tenantId").value("tenant-c"))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.draftVersion.version").value(2))
                .andExpect(jsonPath("$.versionHistory", hasSize(2)));
    }

    @Test
    void shouldRejectPreviewWithoutAdminHeadersAndAcceptPreviewRequestShape() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code,
                    max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_preview', 'preview service', 'SIMPLE_SQL', 'DRAFT', null, 'PG_MAIN', 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'SIMPLE_SQL', 'select * from public.orders where id = /* orderId */1',
                    '[]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false}]',
                    '[]',
                    '{"stage":"SAVE","result":"PASS"}',
                    'tester', 'tester'
                )
                """);

        mockMvc.perform(post("/api/admin/services/1/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "previewParams":{"orderId":1},
                                  "requestContext":{
                                    "tenantId":"tenant-a",
                                    "callerId":"web-admin",
                                    "traceId":"preview-trace-1",
                                    "contextKeys":["tenantId","callerId"]
                                  }
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        Integer deniedAuditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'ADMIN_ACCESS_DENIED' and target_type = 'SERVICE'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(deniedAuditCount).isEqualTo(1);

        mockMvc.perform(post("/api/admin/services/1/preview")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-preview-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "previewParams":{"orderId":1},
                                  "requestContext":{
                                    "tenantId":"tenant-a",
                                    "callerId":"web-admin",
                                    "traceId":"preview-trace-1",
                                    "contextKeys":["tenantId","callerId"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(1))
                .andExpect(jsonPath("$.draftVersion").value(1))
                .andExpect(jsonPath("$.previewParams.orderId").value(1))
                .andExpect(jsonPath("$.requestContext.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.requestContext.callerId").value("web-admin"))
                .andExpect(jsonPath("$.requestContext.traceId").value("preview-trace-1"))
                .andExpect(jsonPath("$.requestContext.contextKeys", hasSize(2)))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"stage\":\"PREVIEW\"")))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"sqlType\":\"SIMPLE_SQL\"")))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"sourceCount\":1")))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"paramCount\":1")))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"logicalPlan\"")))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"nodeType\":\"SCAN\"")))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"joinReorder\"")))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"filterPushdown\"")))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"projectionPushdown\"")))
                .andExpect(jsonPath("$.planSnapshot.stage").value("PREVIEW"))
                .andExpect(jsonPath("$.planSnapshot.sqlType").value("SIMPLE_SQL"))
                .andExpect(jsonPath("$.planSnapshot.sourceCount").value(1))
                .andExpect(jsonPath("$.planSnapshot.paramCount").value(1))
                .andExpect(jsonPath("$.planSnapshot.stages", hasSize(4)))
                .andExpect(jsonPath("$.rows", hasSize(0)))
                .andExpect(jsonPath("$.elapsedMs").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.diagnosticSummary.connectionCode").value("PG_MAIN"))
                .andExpect(jsonPath("$.diagnosticSummary.rowCount").value(0))
                .andExpect(jsonPath("$.diagnosticSummary.truncated").value(false));
    }

    @Test
    void shouldRejectPreviewWhenResourceProtectionConfigMissingOrSqlNotReadonly() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_preview_guard', 'preview guard service', 'SIMPLE_SQL', 'DRAFT', null, null, null, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'SIMPLE_SQL', 'select * from public.orders',
                    '[]', '[]', '[]', '{"stage":"SAVE","result":"PASS"}', 'tester', 'tester'
                )
                """);

        mockMvc.perform(post("/api/admin/services/1/preview")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "previewParams":{},
                                  "requestContext":{"callerId":"web-admin"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("预览执行要求服务配置 maxResultRows"));

        jdbcTemplate.update("""
                update ds_service
                set max_result_rows = 100, query_timeout_seconds = 30
                where id = 1
                """);
        jdbcTemplate.update("""
                update ds_service_version
                set sql_text = 'delete from public.orders'
                where id = 1
                """);

        mockMvc.perform(post("/api/admin/services/1/preview")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "previewParams":{},
                                  "requestContext":{"callerId":"web-admin"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("仅允许 select 或 with 查询语句"));

        Integer previewAuditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'PREVIEW_SERVICE' and operation_result = 'FAILURE'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(previewAuditCount).isEqualTo(2);
        String latestFailureDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'PREVIEW_SERVICE' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(latestFailureDetail).contains("仅允许 select 或 with 查询语句");
    }

    @Test
    void shouldReturnPreviewRowsAndElapsedMsForSimpleSql() throws Exception {
        jdbcTemplate.update("insert into public.orders (id, order_name) values (1, 'order-a')");
        jdbcTemplate.update("insert into public.orders (id, order_name) values (2, 'order-b')");
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    default_connection_code, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_preview_rows', 'preview rows service', 'SIMPLE_SQL', 'DRAFT', null,
                    'PG_MAIN', 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, validation_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'SIMPLE_SQL',
                    'select id, order_name from public.orders where id = /* orderId */1',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]',
                    '[{"fieldName":"id","expression":"id","sortOrder":1},{"fieldName":"order_name","expression":"order_name","sortOrder":2}]',
                    '{"stage":"SAVE","result":"PASS"}',
                    'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_source_capability (
                    connection_id, db_type, capability_code, capability_value, capability_detail_json,
                    scope, scope_value, status, remark
                ) values (
                    (select id from ds_connection where connection_code = 'PG_MAIN'),
                    'POSTGRESQL', 'PROJECT_PUSHDOWN', 'SUPPORTED', '{"projection":"basic"}',
                    'GLOBAL', null, 'ENABLED', 'preview project pushdown'
                )
                """);

        mockMvc.perform(post("/api/admin/services/1/preview")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-preview-rows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "previewParams":{"orderId":1},
                                  "requestContext":{"callerId":"web-admin","traceId":"preview-rows-trace"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.rows[0].id").value(1))
                .andExpect(jsonPath("$.rows[0].order_name").value("order-a"))
                .andExpect(jsonPath("$.elapsedMs").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.diagnosticSummary.connectionCode").value("PG_MAIN"))
                .andExpect(jsonPath("$.diagnosticSummary.dialectRuleCount").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.diagnosticSummary.dialectRewriteApplied").value(false))
                .andExpect(jsonPath("$.diagnosticSummary.rowCount").value(1))
                .andExpect(jsonPath("$.diagnosticSummary.sqlType").value("SIMPLE_SQL"))
                .andExpect(jsonPath("$.diagnosticSummary.maxResultRows").value(100))
                .andExpect(jsonPath("$.diagnosticSummary.truncated").value(false))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"projectionPushdown\"")))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"fieldMapping\"")))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"pushdownFields\":[{\"outputField\":\"id\"")))
                .andExpect(jsonPath("$.planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"outputFields\":[\"id\",\"order_name\"]")));

        Integer previewAuditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'PREVIEW_SERVICE' and operation_result = 'SUCCESS'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(previewAuditCount).isEqualTo(1);
        String successDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'PREVIEW_SERVICE' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(successDetail).contains("\"rowCount\":1");
        org.assertj.core.api.Assertions.assertThat(successDetail).contains("\"previewParamKeys\":[\"orderId\"]");
    }

    @Test
    void shouldDisableServiceAndFilterByStatus() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_disable', 'disable service', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, published_at, published_by, created_by, updated_by
                ) values (
                    1, 1, 1, 'PUBLISHED', 'SIMPLE_SQL', 'select 1', '[]',
                    '[]', '[]', current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_cache_policy (
                    service_id, enabled, ttl_seconds, cache_key_template, max_entries,
                    context_keys_json, remark, deleted, created_by, updated_by
                ) values (
                    1, true, 300, 'data-service:svc_disable:v1', 128,
                    '["tenantId"]', 'disable cache policy', false, 'tester', 'tester'
                )
                """);

        mockMvc.perform(put("/api/admin/services/1/status")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(get("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .param("status", "DISABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].serviceCode").value("svc_disable"));

        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'DISABLE_SERVICE' and operation_result = 'SUCCESS'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(1);

        Boolean cacheEnabled = jdbcTemplate.queryForObject(
                "select enabled from ds_cache_policy where service_id = 1",
                Boolean.class
        );
        String auditDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'DISABLE_SERVICE' order by id desc limit 1",
                String.class
        );

        org.assertj.core.api.Assertions.assertThat(cacheEnabled).isFalse();
        org.assertj.core.api.Assertions.assertThat(auditDetail).contains("\"serviceCode\":\"svc_disable\"");
        org.assertj.core.api.Assertions.assertThat(auditDetail).contains("\"status\":\"DISABLED\"");
        org.assertj.core.api.Assertions.assertThat(auditDetail).contains("\"cachePolicyDisabled\":true");
    }

    @Test
    void shouldRejectDisableWhenServiceIsDraftOrAlreadyDisabled() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_disable_draft', 'disable draft', 'SIMPLE_SQL', 'DRAFT', null,
                    false, 'tester', 'tester'
                )
                """);

        mockMvc.perform(put("/api/admin/services/1/status")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("仅已发布服务允许停用"));

        String firstFailureDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'DISABLE_SERVICE' and operation_result = 'FAILURE' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(firstFailureDetail).contains("\"serviceCode\":\"svc_disable_draft\"");
        org.assertj.core.api.Assertions.assertThat(firstFailureDetail).contains("\"errorMessage\":\"仅已发布服务允许停用\"");

        jdbcTemplate.execute("delete from ds_audit_log");
        jdbcTemplate.execute("delete from ds_service");
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_disable_again', 'disable again', 'SIMPLE_SQL', 'DISABLED', 1,
                    false, 'tester', 'tester'
                )
                """);

        mockMvc.perform(put("/api/admin/services/1/status")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("服务已经是停用状态"));

        String secondFailureDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'DISABLE_SERVICE' and operation_result = 'FAILURE' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(secondFailureDetail).contains("\"serviceCode\":\"svc_disable_again\"");
        org.assertj.core.api.Assertions.assertThat(secondFailureDetail).contains("\"errorMessage\":\"服务已经是停用状态\"");
    }

    @Test
    void shouldPublishDraftAndSwitchCurrentVersion() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_publish', 'publish service', 'FEDERATED_SQL', 'DRAFT', null,
                    10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'FEDERATED_SQL', 'select * from PG_MAIN@public.orders',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[]', '[]', 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_source_capability (
                    connection_id, db_type, capability_code, capability_value, capability_detail_json,
                    scope, scope_value, status, remark
                ) values (
                    (select id from ds_connection where connection_code = 'PG_MAIN'),
                    'POSTGRESQL', 'FILTER_PUSHDOWN', 'SUPPORTED', '{"operators":["=","in"]}',
                    'GLOBAL', null, 'ENABLED', 'publish baseline'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_source_capability (
                    connection_id, db_type, capability_code, capability_value, capability_detail_json,
                    scope, scope_value, status, remark
                ) values (
                    (select id from ds_connection where connection_code = 'PG_MAIN'),
                    'POSTGRESQL', 'PROJECT_PUSHDOWN', 'SUPPORTED', '{"projection":"basic"}',
                    'GLOBAL', null, 'ENABLED', 'publish project baseline'
                )
                """);

        mockMvc.perform(post("/api/admin/services/1/publish")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-service-publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validationSnapshotJson":"{\\"result\\":\\"FRONTEND_SHOULD_NOT_WIN\\"}",
                                  "planSnapshotJson":"{\\"stage\\":\\"CLIENT_PLAN_SHOULD_NOT_WIN\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.draftVersion").doesNotExist())
                .andExpect(jsonPath("$.versionHistory", hasSize(1)))
                .andExpect(jsonPath("$.versionHistory[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.versionHistory[0].validationSnapshotJson").value(org.hamcrest.Matchers.containsString("\"stage\":\"PUBLISH\"")))
                .andExpect(jsonPath("$.versionHistory[0].validationSnapshotJson").value(org.hamcrest.Matchers.containsString("\"message\":\"发布校验通过\"")))
                .andExpect(jsonPath("$.versionHistory[0].validationSnapshotJson").value(org.hamcrest.Matchers.containsString("\"RESOURCE_LIMITS_COMPLETE\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"stage\":\"PUBLISH\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"GENERATE_EXECUTION_PLAN\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"sourceCount\":1")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"logicalPlan\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"nodeType\":\"SCAN\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"joinReorder\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"filterPushdown\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"projectionPushdown\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"capabilitySummaries\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"connectionCode\":\"PG_MAIN\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"ownerLevel\":\"CONNECTION\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"scopeType\":\"GLOBAL\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshot.stage").value("PUBLISH"))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshot.sqlType").value("FEDERATED_SQL"))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshot.sourceCount").value(1))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshot.paramCount").value(0))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshot.stages", hasSize(5)));

        String status = jdbcTemplate.queryForObject(
                "select status from ds_service where id = 1",
                String.class
        );
        Integer currentVersion = jdbcTemplate.queryForObject(
                "select current_version from ds_service where id = 1",
                Integer.class
        );
        String versionStatus = jdbcTemplate.queryForObject(
                "select status from ds_service_version where service_id = 1 and version = 1",
                String.class
        );

        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("PUBLISHED");
        org.assertj.core.api.Assertions.assertThat(currentVersion).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(versionStatus).isEqualTo("PUBLISHED");

        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'PUBLISH_SERVICE'",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(1);

        String auditDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'PUBLISH_SERVICE' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(auditDetail).contains("\"serviceCode\":\"svc_publish\"");
        org.assertj.core.api.Assertions.assertThat(auditDetail).contains("\"version\":1");
    }

    @Test
    void shouldRejectPublishWhenParamTypeStillUnknown() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, default_connection_code, status, current_version,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_publish_param_unknown', 'publish param unknown', 'SIMPLE_SQL', 'PG_MAIN', 'DRAFT', null,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'SIMPLE_SQL',
                    'select * from public.orders where id = /* orderId */1',
                    '[]',
                    '[{"paramName":"orderId","paramType":"UNKNOWN","placeholder":"/* orderId */","collection":false}]',
                    '[]',
                    'tester', 'tester'
                )
                """);

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("SQL 参数类型未确认: orderId"));
    }

    @Test
    void shouldRejectPublishWhenDraftSnapshotDiffersFromReparsedSql() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, default_connection_code, status, current_version,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_publish_snapshot_mismatch', 'publish snapshot mismatch', 'SIMPLE_SQL', 'PG_MAIN', 'DRAFT', null,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'SIMPLE_SQL',
                    'select * from public.orders',
                    '[]',
                    '[]',
                    '[{"fieldName":"*","expression":"*","sortOrder":1}]',
                    'tester', 'tester'
                )
                """);

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("草稿来源快照与当前 SQL 重新解析结果不一致，请重新保存草稿"));
    }

    @Test
    void shouldRejectPublishWhenResourceLimitsMissingOrFederatedCapabilityAbsent() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, default_connection_code, status, current_version,
                    max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_publish_limit_missing', 'publish limit missing', 'SIMPLE_SQL', 'PG_MAIN', 'DRAFT', null,
                    null, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'SIMPLE_SQL', 'select * from public.orders',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[]',
                    '[{"fieldName":"*","expression":"*","sortOrder":1}]',
                    'tester', 'tester'
                )
                """);

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("发布要求服务配置 maxBatchSize"));

        jdbcTemplate.update("delete from ds_audit_log");
        jdbcTemplate.update("delete from ds_service_version");
        jdbcTemplate.update("delete from ds_service");
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_publish_capability_missing', 'publish capability missing', 'FEDERATED_SQL', 'DRAFT', null,
                    10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'FEDERATED_SQL', 'select * from PG_MAIN@public.orders',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[]',
                    '[]',
                    'tester', 'tester'
                )
                """);

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("联邦 SQL 发布前缺少数据源能力配置: connection=PG_MAIN"));
    }

    @Test
    void shouldRejectPublishWhenFederationMatrixCapabilityMissing() throws Exception {
        insertEnabledConnection("PG_ARCHIVE");
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_publish_federation_matrix', 'publish federation matrix', 'FEDERATED_SQL', 'DRAFT', null,
                    10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'FEDERATED_SQL',
                    'select count(*) as total_count from PG_MAIN@public.orders o join PG_ARCHIVE@public.order_items oi on o.id = oi.order_id',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"},{"connectionCode":"PG_ARCHIVE","schemaName":"public","databaseName":"data_service","tableName":"order_items","alias":"oi","dbType":"POSTGRESQL"}]',
                    '[]',
                    '[]',
                    'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_source_capability (
                    connection_id, db_type, capability_code, capability_value, capability_detail_json,
                    scope, scope_value, status, remark
                ) values (
                    (select id from ds_connection where connection_code = 'PG_MAIN'),
                    'POSTGRESQL', 'FILTER_PUSHDOWN', 'SUPPORTED', '{}',
                    'GLOBAL', null, 'ENABLED', 'filter only'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_source_capability (
                    connection_id, db_type, capability_code, capability_value, capability_detail_json,
                    scope, scope_value, status, remark
                ) values (
                    (select id from ds_connection where connection_code = 'PG_ARCHIVE'),
                    'POSTGRESQL', 'FILTER_PUSHDOWN', 'SUPPORTED', '{}',
                    'GLOBAL', null, 'ENABLED', 'filter only'
                )
                """);

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("联邦能力校验失败")));
    }

    @Test
    void shouldRejectPublishWhenFederatedDialectExpressionUnsupported() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_publish_federated_expr', 'publish federated expr', 'FEDERATED_SQL', 'DRAFT', null,
                    10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'FEDERATED_SQL',
                    'select cast(o.id as json) as payload_json from PG_MAIN@public.orders o',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"}]',
                    '[]',
                    '[{"fieldName":"payload_json","expression":"cast(o.id as json)","sortOrder":1}]',
                    'tester', 'tester'
                )
                """);

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("复杂表达式不受支持")));
    }

    @Test
    void shouldRejectPublishWhenLocalCompensationBreaksSemanticBoundary() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_publish_local_comp', 'publish local compensation', 'FEDERATED_SQL', 'DRAFT', null,
                    10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'FEDERATED_SQL',
                    'select count(*) as total_count from PG_MAIN@public.orders o where o.id = /* orderId */1',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"}]',
                    '[{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]',
                    '[]',
                    'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_source_capability (
                    connection_id, db_type, capability_code, capability_value, capability_detail_json,
                    scope, scope_value, status, remark
                ) values (
                    (select id from ds_connection where connection_code = 'PG_MAIN'),
                    'POSTGRESQL', 'FILTER_PUSHDOWN', 'SUPPORTED', '{}',
                    'GLOBAL', null, 'ENABLED', 'filter only'
                )
                """);

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("本地补算校验失败")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SEMANTIC")));
    }

    @Test
    void shouldDisableOldPublishedVersionWhenPublishingNewDraft() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_republish', 'republish service', 'FEDERATED_SQL', 'PUBLISHED', 1,
                    10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, published_at, published_by, created_by, updated_by
                ) values (
                    1, 1, 1, 'PUBLISHED', 'FEDERATED_SQL', 'select * from old_table', '[]',
                    '[]', '[]', current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    2, 1, 2, 'DRAFT', 'FEDERATED_SQL', 'select * from new_table', '[]',
                    '[]', '[]', 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_source_capability (
                    connection_id, db_type, capability_code, capability_value, capability_detail_json,
                    scope, scope_value, status, remark
                ) values (
                    (select id from ds_connection where connection_code = 'PG_MAIN'),
                    'POSTGRESQL', 'FILTER_PUSHDOWN', 'SUPPORTED', '{"operators":["=","in"]}',
                    'GLOBAL', null, 'ENABLED', 'republish baseline'
                )
                """);

        mockMvc.perform(post("/api/admin/services/1/publish")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validationSnapshotJson":"{\\"result\\":\\"IGNORE_ME\\"}",
                                  "planSnapshotJson":"{\\"stage\\":\\"IGNORE_ME_TOO\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.versionHistory", hasSize(2)))
                .andExpect(jsonPath("$.versionHistory[0].version").value(2))
                .andExpect(jsonPath("$.versionHistory[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.versionHistory[0].validationSnapshotJson").value(org.hamcrest.Matchers.containsString("\"stage\":\"PUBLISH\"")))
                .andExpect(jsonPath("$.versionHistory[0].planSnapshotJson").value(org.hamcrest.Matchers.containsString("\"GENERATE_EXECUTION_PLAN\"")))
                .andExpect(jsonPath("$.versionHistory[1].version").value(1))
                .andExpect(jsonPath("$.versionHistory[1].status").value("DISABLED"));
    }

    @Test
    void shouldRollbackVersionSwitchWhenPublishAuditInsertFails() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_publish_txn', 'publish txn service', 'FEDERATED_SQL', 'PUBLISHED', 1,
                    10, 100, 30, 60,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, published_at, published_by, created_by, updated_by
                ) values (
                    1, 1, 1, 'PUBLISHED', 'FEDERATED_SQL', 'select * from PG_MAIN@public.orders',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[]', '[]', current_timestamp, 'tester', 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    2, 1, 2, 'DRAFT', 'FEDERATED_SQL', 'select * from PG_MAIN@public.orders',
                    '[{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":null,"dbType":"POSTGRESQL"}]',
                    '[]', '[]', 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_source_capability (
                    connection_id, db_type, capability_code, capability_value, capability_detail_json,
                    scope, scope_value, status, remark
                ) values (
                    (select id from ds_connection where connection_code = 'PG_MAIN'),
                    'POSTGRESQL', 'FILTER_PUSHDOWN', 'SUPPORTED', '{"operators":["=","in"]}',
                    'GLOBAL', null, 'ENABLED', 'txn baseline'
                )
                """);

        mockMvc.perform(post("/api/admin/services/1/publish")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-publish-rollback-" + "x".repeat(80))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validationSnapshotJson":"{}",
                                  "planSnapshotJson":"{}"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));

        Integer currentVersion = jdbcTemplate.queryForObject(
                "select current_version from ds_service where id = 1",
                Integer.class
        );
        String serviceStatus = jdbcTemplate.queryForObject(
                "select status from ds_service where id = 1",
                String.class
        );
        String publishedVersionStatus = jdbcTemplate.queryForObject(
                "select status from ds_service_version where service_id = 1 and version = 1",
                String.class
        );
        String draftVersionStatus = jdbcTemplate.queryForObject(
                "select status from ds_service_version where service_id = 1 and version = 2",
                String.class
        );

        org.assertj.core.api.Assertions.assertThat(currentVersion).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(serviceStatus).isEqualTo("PUBLISHED");
        org.assertj.core.api.Assertions.assertThat(publishedVersionStatus).isEqualTo("PUBLISHED");
        org.assertj.core.api.Assertions.assertThat(draftVersionStatus).isEqualTo("DRAFT");

        Integer failureAuditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'PUBLISH_SERVICE' and operation_result = 'FAILURE'",
                Integer.class
        );
        String failureAuditDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'PUBLISH_SERVICE' and operation_result = 'FAILURE' order by id desc limit 1",
                String.class
        );
        Integer failureTraceIdLength = jdbcTemplate.queryForObject(
                "select char_length(trace_id) from ds_audit_log where event_type = 'PUBLISH_SERVICE' and operation_result = 'FAILURE' order by id desc limit 1",
                Integer.class
        );

        org.assertj.core.api.Assertions.assertThat(failureAuditCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(failureAuditDetail).contains("\"serviceCode\":\"svc_publish_txn\"");
        org.assertj.core.api.Assertions.assertThat(failureAuditDetail).contains("\"draftVersion\":2");
        org.assertj.core.api.Assertions.assertThat(failureAuditDetail).contains("\"errorMessage\"");
        org.assertj.core.api.Assertions.assertThat(failureTraceIdLength).isEqualTo(64);
    }

    @Test
    void shouldRejectSaveOrPublishWhenSchemaOrTableDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-save-missing-schema")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_missing_schema",
                                  "serviceName":"Missing Schema",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select * from missing_schema.orders",
                                  "maxBatchSize":10,
                                  "maxResultRows":100,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("SQL 引用的 schema/database 不存在: connection=PG_MAIN, schema=missing_schema"));

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .header("X-Trace-Id", "trace-save-missing-table")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_missing_table",
                                  "serviceName":"Missing Table",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select * from public.missing_orders",
                                  "maxBatchSize":10,
                                  "maxResultRows":100,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("SQL 引用的 table 不存在: connection=PG_MAIN, schema=public, table=missing_orders"));

        Integer createFailureAuditCount = jdbcTemplate.queryForObject(
                "select count(1) from ds_audit_log where event_type = 'CREATE_SERVICE' and operation_result = 'FAILURE' and target_id in ('svc_missing_schema', 'svc_missing_table')",
                Integer.class
        );
        String missingSchemaAuditDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'CREATE_SERVICE' and target_id = 'svc_missing_schema' order by id desc limit 1",
                String.class
        );
        String missingTableAuditDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'CREATE_SERVICE' and target_id = 'svc_missing_table' order by id desc limit 1",
                String.class
        );

        org.assertj.core.api.Assertions.assertThat(createFailureAuditCount).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(missingSchemaAuditDetail).contains("\"serviceCode\":\"svc_missing_schema\"");
        org.assertj.core.api.Assertions.assertThat(missingSchemaAuditDetail)
                .contains("\"errorMessage\":\"SQL 引用的 schema/database 不存在: connection=PG_MAIN, schema=missing_schema\"");
        org.assertj.core.api.Assertions.assertThat(missingTableAuditDetail).contains("\"serviceCode\":\"svc_missing_table\"");
        org.assertj.core.api.Assertions.assertThat(missingTableAuditDetail)
                .contains("\"errorMessage\":\"SQL 引用的 table 不存在: connection=PG_MAIN, schema=public, table=missing_orders\"");

        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, default_connection_code, status, current_version,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_publish_missing_table', 'publish missing table', 'SIMPLE_SQL', 'PG_MAIN', 'DRAFT', null,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'SIMPLE_SQL', 'select * from public.orders', '[]',
                    '[]', '[]', 'tester', 'tester'
                )
                """);
        jdbcTemplate.execute("drop table public.orders");

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
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("SQL 引用的 table 不存在: connection=PG_MAIN, schema=public, table=orders"));
    }

    @Test
    void shouldRejectNonReadonlySqlWhenCreatingService() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "serviceCode":"svc_insert",
                                  "serviceName":"Insert Service",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"insert into orders(id) values (1)",
                                  "maxBatchSize":10,
                                  "maxResultRows":100,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("仅允许 select 或 with 查询语句"));
    }

    @Test
    void shouldRejectMultiStatementAndDangerousSqlWhenUpdatingOrPublishing() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_guard', 'guard service', 'SIMPLE_SQL', 'DRAFT', null,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_service_version (
                    id, service_id, version, status, sql_type, sql_text, source_snapshot_json,
                    param_snapshot_json, field_snapshot_json, created_by, updated_by
                ) values (
                    1, 1, 1, 'DRAFT', 'SIMPLE_SQL', 'select 1', '[]',
                    '[]', '[]', 'tester', 'tester'
                )
                """);

        mockMvc.perform(put("/api/admin/services/1")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName":"guard service",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MAIN",
                                  "sqlText":"select 1; select 2",
                                  "maxBatchSize":10,
                                  "maxResultRows":100,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("不允许多语句 SQL"));

        String updateFailureAuditDetail = jdbcTemplate.queryForObject(
                "select detail_json from ds_audit_log where event_type = 'UPDATE_SERVICE' and target_id = 'svc_guard' and operation_result = 'FAILURE' order by id desc limit 1",
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(updateFailureAuditDetail).contains("\"serviceCode\":\"svc_guard\"");
        org.assertj.core.api.Assertions.assertThat(updateFailureAuditDetail).contains("\"errorMessage\":\"不允许多语句 SQL\"");

        jdbcTemplate.update("""
                update ds_service_version
                set sql_text = 'select pg_sleep(1)'
                where id = 1
                """);

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("SQL 包含危险函数调用"));

        jdbcTemplate.update("""
                update ds_service_version
                set sql_text = 'call do_something()'
                where id = 1
                """);

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("仅允许 select 或 with 查询语句"));
    }

    @Test
    void shouldRejectSimpleSqlWithoutEnabledDefaultConnection() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_simple_no_conn",
                                  "serviceName":"Simple No Conn",
                                  "sqlType":"SIMPLE_SQL",
                                  "sqlText":"select * from public.orders",
                                  "maxBatchSize":10,
                                  "maxResultRows":100,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("简单 SQL 必须指定默认连接"));

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_simple_missing_conn",
                                  "serviceName":"Simple Missing Conn",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_MISSING",
                                  "sqlText":"select * from public.orders",
                                  "maxBatchSize":10,
                                  "maxResultRows":100,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("默认连接不存在: PG_MISSING"));

        jdbcTemplate.update("""
                insert into ds_connection (
                    id, connection_code, connection_name, db_type, host, port, username,
                    password_ciphertext, status, deleted, created_by, updated_by
                ) values (
                    1, 'PG_DISABLED', 'pg disabled', 'POSTGRESQL', '127.0.0.1', 5432, 'postgres',
                    'cipher', 'DISABLED', false, 'tester', 'tester'
                )
                """);

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceCode":"svc_simple_disabled_conn",
                                  "serviceName":"Simple Disabled Conn",
                                  "sqlType":"SIMPLE_SQL",
                                  "defaultConnectionCode":"PG_DISABLED",
                                  "sqlText":"select * from public.orders",
                                  "maxBatchSize":10,
                                  "maxResultRows":100,
                                  "queryTimeoutSeconds":30,
                                  "federatedQueryTimeoutSeconds":60
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATASOURCE_DISABLED_IN_USE"))
                .andExpect(jsonPath("$.message").value("默认连接未启用: PG_DISABLED"));
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
