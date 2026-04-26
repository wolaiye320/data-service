package cn.dtkeys.dataservice.core.meta;

import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MetadataRepositoryTestApplication.class)
@ActiveProfiles("test")
class MetadataSchemaVerificationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldCreateSqlFirstMetadataTablesAndColumns() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'ds_connection',
                    'ds_service',
                    'ds_service_version',
                    'ds_cache_policy',
                    'ds_source_capability',
                    'ds_dialect_rule',
                    'ds_audit_log'
                  )
                order by table_name
                """, String.class);

        assertThat(tables).containsExactly(
                "ds_audit_log",
                "ds_cache_policy",
                "ds_connection",
                "ds_dialect_rule",
                "ds_service",
                "ds_service_version",
                "ds_source_capability"
        );

        List<String> serviceColumns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'ds_service'
                order by ordinal_position
                """, String.class);
        assertThat(serviceColumns).contains(
                "default_connection_code"
        );

        List<String> serviceVersionColumns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'ds_service_version'
                order by ordinal_position
                """, String.class);
        assertThat(serviceVersionColumns).contains(
                "sql_type",
                "sql_text",
                "source_snapshot_json",
                "param_snapshot_json",
                "field_snapshot_json",
                "validation_snapshot_json",
                "plan_snapshot_json",
                "request_context_snapshot_json",
                "published_at",
                "published_by"
        );

        List<String> indexes = jdbcTemplate.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'public'
                  and indexname in (
                    'idx_ds_connection_status_created',
                    'idx_ds_service_default_connection_code',
                    'idx_ds_service_status_created',
                    'idx_ds_service_current_version',
                    'idx_ds_service_version_status_created',
                    'idx_ds_cache_policy_service_enabled',
                    'idx_ds_source_capability_connection_status',
                    'uk_ds_source_capability_connection_scope',
                    'idx_ds_dialect_rule_connection_status',
                    'idx_ds_dialect_rule_db_type_status'
                  )
                order by indexname
                """, String.class);
        assertThat(indexes).containsExactly(
                "idx_ds_cache_policy_service_enabled",
                "idx_ds_connection_status_created",
                "idx_ds_dialect_rule_connection_status",
                "idx_ds_dialect_rule_db_type_status",
                "idx_ds_service_current_version",
                "idx_ds_service_default_connection_code",
                "idx_ds_service_status_created",
                "idx_ds_service_version_status_created",
                "idx_ds_source_capability_connection_status",
                "uk_ds_source_capability_connection_scope"
        );
    }

    @Test
    void shouldSeedDefaultDialectRules() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V3__seed_default_dialect_rules.sql"));
        }

        List<String> seededRules = jdbcTemplate.queryForList("""
                select db_type || ':' || rule_code
                from ds_dialect_rule
                where connection_id is null
                  and db_type in ('POSTGRESQL', 'MYSQL', 'ORACLE')
                order by db_type, rule_code
                """, String.class);

        assertThat(seededRules).containsExactly(
                "MYSQL:CURRENT_TIMESTAMP",
                "MYSQL:STRING_CONCAT",
                "ORACLE:CURRENT_TIMESTAMP",
                "ORACLE:STRING_CONCAT",
                "POSTGRESQL:CURRENT_TIMESTAMP",
                "POSTGRESQL:STRING_CONCAT"
        );
    }
}
