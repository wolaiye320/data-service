package cn.dtkeys.dataservice.app;

import cn.dtkeys.dataservice.audit.model.DSAuditLog;
import cn.dtkeys.dataservice.repository.DSAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditAndPermissionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DSAuditLogRepository dsAuditLogRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("truncate table ds_audit_log restart identity cascade");
        jdbcTemplate.execute("truncate table ds_connection restart identity cascade");
    }

    @Test
    void shouldRejectAuditListForCallerRole() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("X-Operator", "caller-a")
                .header("X-Operator-Role", "CALLER")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowAuditListForAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("X-Operator", "admin-a")
                .header("X-Operator-Role", "ADMIN")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.total").value(0))
            .andExpect(jsonPath("$.meta.pageNo").value(1))
            .andExpect(jsonPath("$.meta.pageSize").value(20));
    }

    @Test
    void shouldPersistAuditLogWhenViewingPlatformBaseline() throws Exception {
        mockMvc.perform(get("/api/platform/baseline")
                .header("X-Operator", "admin-a")
                .header("X-Operator-Role", "ADMIN")
                .header("X-Forwarded-For", "10.10.1.8")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        List<DSAuditLog> records = dsAuditLogRepository.search(null, null, "admin-a", null, null,
            null, null, 20, 0);
        assertThat(records).hasSize(1);
        DSAuditLog auditLog = records.get(0);
        assertThat(auditLog.getEventType()).isEqualTo("VIEW_PLATFORM_BASELINE");
        assertThat(auditLog.getOperator()).isEqualTo("admin-a");
        assertThat(auditLog.getOperatorRole()).isEqualTo("ADMIN");
        assertThat(auditLog.getRequestIp()).isEqualTo("10.10.1.8");
        assertThat(auditLog.getDetailJson()).contains("traceId");
    }

    @Test
    void shouldRejectConnectionTestForCallerRole() throws Exception {
        jdbcTemplate.execute("""
            insert into ds_connection (id, connection_code, connection_name, db_type, host, port, username, password_ciphertext,
                                       status, deleted, created_by, updated_by, connection_config_json)
            values (1, 'bank_conn', 'Bank Connection', 'POSTGRESQL', 'localhost', 5432, 'postgres', 'postgres',
                    'ENABLED', false, 'tester', 'tester', '{"database":"data_service"}')
            """);

        mockMvc.perform(post("/api/admin/connections/1/test")
                .header("X-Operator", "caller-a")
                .header("X-Operator-Role", "CALLER")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
