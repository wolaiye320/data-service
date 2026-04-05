package cn.dtkeys.dataservice.infrastructure.repository;

import cn.dtkeys.dataservice.domain.model.DSCachePolicy;
import cn.dtkeys.dataservice.domain.model.DSAuditLog;
import cn.dtkeys.dataservice.domain.model.DSCatalog;
import cn.dtkeys.dataservice.domain.model.DSConnection;
import cn.dtkeys.dataservice.domain.model.DSDefinition;
import cn.dtkeys.dataservice.domain.model.DSField;
import cn.dtkeys.dataservice.domain.model.DSParam;
import cn.dtkeys.dataservice.domain.model.DSSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MetadataRepositoryIntegrationTest.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "mybatis.configuration.map-underscore-to-camel-case=true"
})
class MetadataRepositoryIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @org.mybatis.spring.annotation.MapperScan("cn.dtkeys.dataservice.infrastructure.repository")
    @EnableConfigurationProperties
    static class TestApplication {
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/data_service");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void cleanupTables() {
        jdbcTemplate.execute("""
            truncate table ds_audit_log restart identity cascade
            """);
        jdbcTemplate.execute("""
            truncate table ds_cache_policy, ds_field, ds_param, ds_source, ds_catalog, ds_service restart identity cascade
            """);
        jdbcTemplate.execute("""
            truncate table ds_connection restart identity cascade
            """);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DSConnectionRepository dsConnectionRepository;

    @Autowired
    private DSCatalogRepository dsCatalogRepository;

    @Autowired
    private DSDefinitionRepository dsDefinitionRepository;

    @Autowired
    private DSSourceRepository dsSourceRepository;

    @Autowired
    private DSParamRepository dsParamRepository;

    @Autowired
    private DSFieldRepository dsFieldRepository;

    @Autowired
    private DSCachePolicyRepository dsCachePolicyRepository;

    @Autowired
    private DSAuditLogRepository dsAuditLogRepository;

    @Test
    void shouldInsertAndQueryMetadataAggregates() {
        DSConnection connection = new DSConnection();
        connection.setConnectionCode("pg_bank");
        connection.setConnectionName("Bank PostgreSQL");
        connection.setDbType("POSTGRESQL");
        connection.setHost("127.0.0.1");
        connection.setPort(5432);
        connection.setUsername("postgres");
        connection.setPasswordCiphertext("cipher");
        connection.setStatus("ENABLED");
        connection.setDeleted(false);
        connection.setCreatedBy("tester");
        connection.setUpdatedBy("tester");
        dsConnectionRepository.insert(connection);

        DSCatalog catalog = new DSCatalog();
        catalog.setConnectionId(connection.getId());
        catalog.setCatalogCode("bank_public");
        catalog.setCatalogName("bankdb.public");
        catalog.setCatalogType("SCHEMA");
        catalog.setCatalogValue("public");
        catalog.setStatus("ENABLED");
        catalog.setDeleted(false);
        catalog.setCreatedBy("tester");
        catalog.setUpdatedBy("tester");
        dsCatalogRepository.insert(catalog);

        DSDefinition definition = new DSDefinition();
        definition.setServiceCode("customer_profile_query");
        definition.setServiceName("客户画像查询");
        definition.setServiceType("SIMPLE_QUERY");
        definition.setStatus("DRAFT");
        definition.setSqlTemplate("select customer_id from customer where customer_id = :customerId");
        definition.setSqlType("SIMPLE_SQL");
        definition.setExecutionMode("REMOTE_ONLY");
        definition.setPlanStatus("UNPLANNED");
        definition.setVersion(0);
        definition.setMaxBatchSize(20);
        definition.setMaxResultRows(200);
        definition.setQueryTimeoutSeconds(20);
        definition.setFederatedQueryTimeoutSeconds(40);
        definition.setDeleted(false);
        definition.setCreatedBy("tester");
        definition.setUpdatedBy("tester");
        dsDefinitionRepository.insert(definition);

        DSSource source = new DSSource();
        source.setServiceId(definition.getId());
        source.setConnectionId(connection.getId());
        source.setCatalogId(catalog.getId());
        source.setSourceAlias("customer");
        source.setSourceType("TABLE");
        source.setSourceValue("customer");
        source.setStatus("ENABLED");
        source.setDeleted(false);
        source.setCreatedBy("tester");
        source.setUpdatedBy("tester");
        dsSourceRepository.insert(source);

        DSParam param = new DSParam();
        param.setServiceId(definition.getId());
        param.setParamName("customerId");
        param.setDisplayName("客户号");
        param.setParamType("STRING");
        param.setSqlPlaceholder("customerId");
        param.setRequired(true);
        param.setSortOrder(1);
        param.setDeleted(false);
        param.setCreatedBy("tester");
        param.setUpdatedBy("tester");
        dsParamRepository.insert(param);

        DSField field = new DSField();
        field.setServiceId(definition.getId());
        field.setSourceAlias("customer");
        field.setSourceColumn("customer_id");
        field.setFieldName("customerId");
        field.setDisplayName("客户号");
        field.setFieldType("STRING");
        field.setSortOrder(1);
        field.setPrimaryKey(true);
        field.setJoinKey(true);
        field.setDeleted(false);
        field.setCreatedBy("tester");
        field.setUpdatedBy("tester");
        dsFieldRepository.insert(field);

        DSCachePolicy cachePolicy = new DSCachePolicy();
        cachePolicy.setServiceId(definition.getId());
        cachePolicy.setEnabled(true);
        cachePolicy.setTtlSeconds(600);
        cachePolicy.setCacheKeyTemplate("data-service:{serviceCode}:v{version}:{paramHash}");
        cachePolicy.setMaxEntries(200);
        cachePolicy.setDeleted(false);
        cachePolicy.setCreatedBy("tester");
        cachePolicy.setUpdatedBy("tester");
        dsCachePolicyRepository.insert(cachePolicy);

        assertThat(dsConnectionRepository.findByCode("pg_bank").getConnectionName()).isEqualTo("Bank PostgreSQL");
        assertThat(dsCatalogRepository.findByConnectionId(connection.getId()))
            .extracting(DSCatalog::getCatalogCode)
            .containsExactly("bank_public");
        assertThat(dsDefinitionRepository.findByServiceCode("customer_profile_query").getServiceName())
            .isEqualTo("客户画像查询");
        assertThat(dsSourceRepository.findByServiceId(definition.getId()))
            .extracting(DSSource::getSourceAlias)
            .containsExactly("customer");
        assertThat(dsParamRepository.findByServiceId(definition.getId()))
            .extracting(DSParam::getParamName)
            .containsExactly("customerId");
        assertThat(dsFieldRepository.findByServiceId(definition.getId()))
            .extracting(DSField::getFieldName)
            .containsExactly("customerId");
        assertThat(dsCachePolicyRepository.findByServiceId(definition.getId()).getTtlSeconds()).isEqualTo(600);
    }

    @Test
    void shouldExposeRequiredSchemaColumnsAfterFlywayMigration() {
        jdbcTemplate.execute("""
            select id, connection_code, connection_name, db_type, host, port, username, password_ciphertext, status
            from ds_connection
            limit 1
            """);
        jdbcTemplate.execute("""
            select id, service_code, service_name, status, sql_template, max_batch_size, max_result_rows,
                   query_timeout_seconds, federated_query_timeout_seconds
            from ds_service
            limit 1
            """);
        jdbcTemplate.execute("""
            select id, service_id, param_name, sql_placeholder, required, sort_order
            from ds_param
            limit 1
            """);
        jdbcTemplate.execute("""
            select id, service_id, field_name, source_column, field_type
            from ds_field
            limit 1
            """);
        jdbcTemplate.execute("""
            select id, service_id, enabled, ttl_seconds, cache_key_template
            from ds_cache_policy
            limit 1
            """);
        jdbcTemplate.execute("""
            select id, service_id, connection_id, event_type, target_type, target_id, operator, operator_role,
                   operation_result, trace_id, request_ip, change_summary, detail_json, created_at, created_by
            from ds_audit_log
            limit 1
            """);
    }

    @Test
    void shouldInsertAndSearchAuditLog() {
        DSAuditLog auditLog = new DSAuditLog();
        auditLog.setEventType("VIEW_PLATFORM_BASELINE");
        auditLog.setTargetType("PLATFORM_BASELINE");
        auditLog.setTargetId("runtime-baseline");
        auditLog.setOperator("admin-a");
        auditLog.setOperatorRole("ADMIN");
        auditLog.setOperationResult("SUCCESS");
        auditLog.setTraceId("trace-001");
        auditLog.setRequestIp("10.10.1.8");
        auditLog.setChangeSummary("查看平台运行基线");
        auditLog.setDetailJson("{\"traceId\":\"trace-001\"}");
        auditLog.setCreatedAt(java.time.LocalDateTime.now());
        auditLog.setCreatedBy("admin-a");
        dsAuditLogRepository.insert(auditLog);

        assertThat(auditLog.getId()).isNotNull();
        assertThat(dsAuditLogRepository.count(null, null, "admin-a", "VIEW_PLATFORM_BASELINE", "SUCCESS",
            null, null)).isEqualTo(1);
        assertThat(dsAuditLogRepository.search(null, null, "admin-a", null, null, null, null, 10, 0))
            .extracting(DSAuditLog::getTargetId)
            .containsExactly("runtime-baseline");
        assertThat(dsAuditLogRepository.findById(auditLog.getId()).getOperatorRole()).isEqualTo("ADMIN");
    }
}
