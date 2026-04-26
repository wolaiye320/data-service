package cn.dtkeys.dataservice.core.meta;

import cn.dtkeys.dataservice.core.meta.domain.DsAuditLogRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsCachePolicyRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsDialectRuleRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsSourceCapabilityRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsAuditLogRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsCachePolicyRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsConnectionRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsDialectRuleRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsServiceRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsServiceVersionRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsSourceCapabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = MetadataRepositoryTestApplication.class)
@ActiveProfiles("test")
class MetadataRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DsConnectionRepository connectionRepository;

    @Autowired
    private DsServiceRepository serviceRepository;

    @Autowired
    private DsServiceVersionRepository serviceVersionRepository;

    @Autowired
    private DsCachePolicyRepository cachePolicyRepository;

    @Autowired
    private DsSourceCapabilityRepository sourceCapabilityRepository;

    @Autowired
    private DsDialectRuleRepository dialectRuleRepository;

    @Autowired
    private DsAuditLogRepository auditLogRepository;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("delete from ds_audit_log");
        jdbcTemplate.execute("delete from ds_dialect_rule where connection_id is not null");
        jdbcTemplate.execute("delete from ds_source_capability");
        jdbcTemplate.execute("delete from ds_cache_policy");
        jdbcTemplate.execute("delete from ds_service_version");
        jdbcTemplate.execute("delete from ds_service");
        jdbcTemplate.execute("delete from ds_connection");
    }

    @Test
    void shouldPersistAndQuerySqlFirstMetadataChain() {
        DsConnectionRecord connection = new DsConnectionRecord();
        connection.setConnectionCode("PG_MAIN");
        connection.setConnectionName("PostgreSQL Main");
        connection.setDbType("POSTGRESQL");
        connection.setHost("127.0.0.1");
        connection.setPort(5432);
        connection.setUsername("postgres");
        connection.setPasswordCiphertext("cipher");
        connection.setStatus("ENABLED");
        connection.setRemark("main");
        connection.setConnectionConfigJson("{\"ssl\":false}");
        connection.setDeleted(false);
        connection.setCreatedBy("tester");
        connection.setUpdatedBy("tester");
        connectionRepository.insert(connection);

        DsServiceRecord service = new DsServiceRecord();
        service.setServiceCode("svc_orders");
        service.setServiceName("Orders Query");
        service.setSqlType("FEDERATED_SQL");
        service.setDefaultConnectionCode(null);
        service.setStatus("DRAFT");
        service.setCurrentVersion(null);
        service.setMaxBatchSize(10);
        service.setMaxResultRows(500);
        service.setQueryTimeoutSeconds(30);
        service.setFederatedQueryTimeoutSeconds(60);
        service.setTenantId(null);
        service.setRemark("orders");
        service.setDeleted(false);
        service.setCreatedBy("tester");
        service.setUpdatedBy("tester");
        serviceRepository.insert(service);

        DsServiceVersionRecord version = new DsServiceVersionRecord();
        version.setServiceId(service.getId());
        version.setVersion(1);
        version.setStatus("DRAFT");
        version.setSqlType("FEDERATED_SQL");
        version.setSqlText("select * from PG_MAIN@public.orders o");
        version.setSourceSnapshotJson("[{\"connectionCode\":\"PG_MAIN\"}]");
        version.setParamSnapshotJson("[]");
        version.setFieldSnapshotJson("[{\"fieldName\":\"order_id\"}]");
        version.setValidationSnapshotJson("{\"result\":\"PASS\"}");
        version.setPlanSnapshotJson("{\"stage\":\"SAVE\"}");
        version.setRequestContextSnapshotJson("{\"traceId\":\"t1\"}");
        version.setCreatedBy("tester");
        version.setUpdatedBy("tester");
        serviceVersionRepository.insert(version);

        serviceVersionRepository.updatePublishState(
                version.getId(),
                "PUBLISHED",
                LocalDateTime.of(2026, 4, 24, 10, 0),
                "tester",
                "tester"
        );
        serviceRepository.updateStatus(service.getId(), "PUBLISHED", "tester");
        serviceRepository.updateCurrentVersion(service.getId(), 1, "tester");

        DsCachePolicyRecord cachePolicy = new DsCachePolicyRecord();
        cachePolicy.setServiceId(service.getId());
        cachePolicy.setEnabled(true);
        cachePolicy.setTtlSeconds(300);
        cachePolicy.setCacheKeyTemplate("data-service:{serviceCode}:v{version}:{paramHash}");
        cachePolicy.setMaxEntries(1000);
        cachePolicy.setContextKeysJson("[\"tenantId\",\"callerId\"]");
        cachePolicy.setRemark("default");
        cachePolicy.setDeleted(false);
        cachePolicy.setCreatedBy("tester");
        cachePolicy.setUpdatedBy("tester");
        cachePolicyRepository.insert(cachePolicy);

        DsSourceCapabilityRecord capability = new DsSourceCapabilityRecord();
        capability.setConnectionId(connection.getId());
        capability.setDbType("POSTGRESQL");
        capability.setCapabilityCode("FILTER_PUSHDOWN");
        capability.setCapabilityValue("SUPPORTED");
        capability.setCapabilityDetailJson("{\"operators\":[\"=\",\"in\"]}");
        capability.setScope("GLOBAL");
        capability.setScopeValue(null);
        capability.setStatus("ENABLED");
        capability.setRemark("ok");
        sourceCapabilityRepository.insert(capability);

        DsDialectRuleRecord dialectRule = new DsDialectRuleRecord();
        dialectRule.setConnectionId(connection.getId());
        dialectRule.setDbType("POSTGRESQL");
        dialectRule.setRuleCode("STRING_CONCAT");
        dialectRule.setRuleType("FUNCTION_MAPPING");
        dialectRule.setRuleConfig("{\"source\":\"concat\",\"target\":\"concat\"}");
        dialectRule.setStatus("ENABLED");
        dialectRule.setRemark("concat");
        dialectRuleRepository.insert(dialectRule);

        DsAuditLogRecord auditLog = new DsAuditLogRecord();
        auditLog.setServiceId(service.getId());
        auditLog.setConnectionId(connection.getId());
        auditLog.setEventType("PUBLISH");
        auditLog.setTargetType("SERVICE");
        auditLog.setTargetId(service.getServiceCode());
        auditLog.setOperator("tester");
        auditLog.setOperatorRole("ADMIN");
        auditLog.setOperationResult("SUCCESS");
        auditLog.setTraceId("trace-1");
        auditLog.setRequestIp("127.0.0.1");
        auditLog.setChangeSummary("publish success");
        auditLog.setDetailJson("{\"version\":1}");
        auditLog.setContextSummaryJson("{\"callerId\":\"unit-test\"}");
        auditLog.setCreatedBy("tester");
        auditLogRepository.insert(auditLog);

        DsConnectionRecord loadedConnection = connectionRepository.findByConnectionCode("PG_MAIN");
        DsServiceRecord loadedService = serviceRepository.findByServiceCode("svc_orders");
        DsServiceVersionRecord loadedVersion = serviceVersionRepository.findByServiceIdAndVersion(service.getId(), 1);
        DsCachePolicyRecord loadedCachePolicy = cachePolicyRepository.findByServiceId(service.getId());

        assertThat(loadedConnection).isNotNull();
        assertThat(loadedConnection.getConnectionName()).isEqualTo("PostgreSQL Main");
        assertThat(connectionRepository.findByStatus("ENABLED")).hasSize(1);

        assertThat(loadedService).isNotNull();
        assertThat(loadedService.getDefaultConnectionCode()).isNull();
        assertThat(loadedService.getCurrentVersion()).isEqualTo(1);
        assertThat(serviceRepository.findByStatus("DRAFT")).isEmpty();
        assertThat(serviceRepository.findByStatus("PUBLISHED")).hasSize(1);

        assertThat(loadedVersion).isNotNull();
        assertThat(loadedVersion.getStatus()).isEqualTo("PUBLISHED");
        assertThat(serviceVersionRepository.findHistoryByServiceId(service.getId())).hasSize(1);

        assertThat(loadedCachePolicy).isNotNull();
        assertThat(loadedCachePolicy.getContextKeysJson()).contains("tenantId");
        assertThat(sourceCapabilityRepository.findEnabledByConnectionId(connection.getId())).hasSize(1);
        assertThat(sourceCapabilityRepository.findEnabledByConnectionId(connection.getId()).getFirst().getScope()).isEqualTo("GLOBAL");
        assertThat(dialectRuleRepository.findEnabledByConnectionId(connection.getId())).hasSize(1);
        assertThat(dialectRuleRepository.findEnabledByDbType("POSTGRESQL"))
                .extracting(DsDialectRuleRecord::getRuleCode)
                .contains("STRING_CONCAT", "CURRENT_TIMESTAMP");
        assertThat(auditLogRepository.findByServiceId(service.getId())).hasSize(1);
        assertThat(auditLogRepository.findByConnectionId(connection.getId())).hasSize(1);
    }

    @Test
    void shouldEnforceUniqueConstraintsAndStatusChecks() {
        DsConnectionRecord connection = new DsConnectionRecord();
        connection.setConnectionCode("PG_DUP");
        connection.setConnectionName("dup");
        connection.setDbType("POSTGRESQL");
        connection.setHost("127.0.0.1");
        connection.setPort(5432);
        connection.setUsername("postgres");
        connection.setPasswordCiphertext("cipher");
        connection.setStatus("ENABLED");
        connection.setDeleted(false);
        connection.setCreatedBy("tester");
        connection.setUpdatedBy("tester");
        connectionRepository.insert(connection);

        DsConnectionRecord duplicate = new DsConnectionRecord();
        duplicate.setConnectionCode("PG_DUP");
        duplicate.setConnectionName("dup2");
        duplicate.setDbType("POSTGRESQL");
        duplicate.setHost("127.0.0.1");
        duplicate.setPort(5432);
        duplicate.setUsername("postgres");
        duplicate.setPasswordCiphertext("cipher");
        duplicate.setStatus("ENABLED");
        duplicate.setDeleted(false);
        duplicate.setCreatedBy("tester");
        duplicate.setUpdatedBy("tester");

        assertThatThrownBy(() -> connectionRepository.insert(duplicate))
                .hasMessageContaining("uk_ds_connection_code");

        DsServiceRecord invalidStatusService = new DsServiceRecord();
        invalidStatusService.setServiceCode("svc_invalid");
        invalidStatusService.setServiceName("invalid");
        invalidStatusService.setSqlType("SIMPLE_SQL");
        invalidStatusService.setDefaultConnectionCode("PG_DUP");
        invalidStatusService.setStatus("UNKNOWN");
        invalidStatusService.setDeleted(false);
        invalidStatusService.setCreatedBy("tester");
        invalidStatusService.setUpdatedBy("tester");

        assertThatThrownBy(() -> serviceRepository.insert(invalidStatusService))
                .hasMessageContaining("chk_ds_service_status");
    }

    @Test
    void shouldPersistDefaultConnectionCodeForSimpleSqlService() {
        DsConnectionRecord connection = new DsConnectionRecord();
        connection.setConnectionCode("PG_SIMPLE");
        connection.setConnectionName("pg simple");
        connection.setDbType("POSTGRESQL");
        connection.setHost("127.0.0.1");
        connection.setPort(5432);
        connection.setUsername("postgres");
        connection.setPasswordCiphertext("cipher");
        connection.setStatus("ENABLED");
        connection.setDeleted(false);
        connection.setCreatedBy("tester");
        connection.setUpdatedBy("tester");
        connectionRepository.insert(connection);

        DsServiceRecord service = new DsServiceRecord();
        service.setServiceCode("svc_simple_default_conn");
        service.setServiceName("simple");
        service.setSqlType("SIMPLE_SQL");
        service.setDefaultConnectionCode("PG_SIMPLE");
        service.setStatus("DRAFT");
        service.setDeleted(false);
        service.setCreatedBy("tester");
        service.setUpdatedBy("tester");
        serviceRepository.insert(service);

        DsServiceRecord loadedService = serviceRepository.findByServiceCode("svc_simple_default_conn");
        assertThat(loadedService).isNotNull();
        assertThat(loadedService.getDefaultConnectionCode()).isEqualTo("PG_SIMPLE");
    }
}
