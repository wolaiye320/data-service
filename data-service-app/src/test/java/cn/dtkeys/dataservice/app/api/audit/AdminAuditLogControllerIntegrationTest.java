package cn.dtkeys.dataservice.app.api.audit;

import cn.dtkeys.dataservice.app.DataServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DataServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditLogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("delete from ds_audit_log");
        jdbcTemplate.execute("delete from ds_service_version");
        jdbcTemplate.execute("delete from ds_service");
    }

    @Test
    void shouldListAuditsByServiceOperatorAndTime() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_audit', 'audit service', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_audit_log (
                    service_id, event_type, target_type, target_id,
                    operator, operator_role, operation_result, trace_id,
                    change_summary, detail_json, context_summary_json, created_at, created_by
                ) values
                (1, 'QUERY_EXECUTE', 'SERVICE', 'svc_audit', 'caller-a', 'CALLER', 'SUCCESS', 'trace-1',
                 'query success', '{\"queryStatus\":\"SUCCESS\"}', '{\"traceId\":\"trace-1\"}', timestamp '2026-04-25 10:00:00', 'caller-a'),
                (1, 'QUERY_EXECUTE', 'SERVICE', 'svc_audit', 'caller-a', 'CALLER', 'FAILURE', 'trace-2',
                 'query failure', '{\"queryStatus\":\"FAILURE\"}', '{\"traceId\":\"trace-2\"}', timestamp '2026-04-25 11:00:00', 'caller-a'),
                (1, 'PREVIEW_SERVICE', 'SERVICE', 'svc_audit', 'web-admin', 'ADMIN', 'SUCCESS', 'trace-3',
                 'preview success', '{\"draftVersion\":1}', '{\"traceId\":\"trace-3\"}', timestamp '2026-04-24 09:00:00', 'web-admin')
                """);

        mockMvc.perform(get("/api/admin/audits")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN")
                        .param("serviceCode", "svc_audit")
                        .param("operator", "caller-a")
                        .param("eventType", "QUERY_EXECUTE")
                        .param("operationResult", "FAILURE")
                        .param("startAt", "2026-04-25T00:00:00")
                        .param("endAt", "2026-04-25T23:59:59")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].serviceCode").value("svc_audit"))
                .andExpect(jsonPath("$.items[0].eventType").value("QUERY_EXECUTE"))
                .andExpect(jsonPath("$.items[0].operator").value("caller-a"))
                .andExpect(jsonPath("$.items[0].operationResult").value("FAILURE"));
    }

    @Test
    void shouldReturnAuditDetailWithMaskedSensitiveFields() throws Exception {
        jdbcTemplate.update("""
                insert into ds_service (
                    id, service_code, service_name, sql_type, status, current_version,
                    deleted, created_by, updated_by
                ) values (
                    1, 'svc_audit', 'audit service', 'SIMPLE_SQL', 'PUBLISHED', 1,
                    false, 'tester', 'tester'
                )
                """);
        jdbcTemplate.update("""
                insert into ds_audit_log (
                    id, service_id, event_type, target_type, target_id,
                    operator, operator_role, operation_result, trace_id,
                    change_summary, detail_json, context_summary_json, created_by
                ) values (
                    99, 1, 'CREATE_CONNECTION', 'SERVICE', 'svc_audit',
                    'web-admin', 'ADMIN', 'SUCCESS', 'trace-audit-detail',
                    'detail check',
                    '{"passwordCiphertext":"abc","nested":{"token":"secret-token"},"serviceCode":"svc_audit"}',
                    '{"callerId":"web-admin","credential":"secret-credential"}',
                    'web-admin'
                )
                """);

        mockMvc.perform(get("/api/admin/audits/99")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.serviceCode").value("svc_audit"))
                .andExpect(jsonPath("$.detailJson").value(containsString("\"passwordCiphertext\":\"***\"")))
                .andExpect(jsonPath("$.detailJson").value(containsString("\"token\":\"***\"")))
                .andExpect(jsonPath("$.contextSummaryJson").value(containsString("\"credential\":\"***\"")));
    }

    @Test
    void shouldMaskPlainTextSensitiveAuditDetail() throws Exception {
        jdbcTemplate.update("""
                insert into ds_audit_log (
                    id, event_type, target_type, target_id,
                    operator, operator_role, operation_result, trace_id,
                    change_summary, detail_json, context_summary_json, created_by
                ) values (
                    100, 'ADMIN_ACCESS_DENIED', 'SERVICE', 'svc_x',
                    'web-admin', 'ADMIN', 'FAILURE', 'trace-sensitive-text',
                    'detail check',
                    'password=plain-secret',
                    '{"traceId":"trace-sensitive-text"}',
                    'web-admin'
                )
                """);

        mockMvc.perform(get("/api/admin/audits/100")
                        .header("X-Operator", "web-admin")
                        .header("X-Operator-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailJson").value("***"));
    }
}
