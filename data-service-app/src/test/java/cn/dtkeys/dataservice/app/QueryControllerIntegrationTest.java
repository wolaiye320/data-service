package cn.dtkeys.dataservice.app;

import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.service.model.DSDefinition;
import cn.dtkeys.dataservice.service.model.DSField;
import cn.dtkeys.dataservice.service.model.DSParam;
import cn.dtkeys.dataservice.service.model.DSSource;
import cn.dtkeys.dataservice.query.cache.QueryCache;
import cn.dtkeys.dataservice.datasource.DatasourceConnectionManager;
import cn.dtkeys.dataservice.query.protection.ResourceProtectionProperties;
import cn.dtkeys.dataservice.query.protection.ResourceProtectionService;
import cn.dtkeys.dataservice.repository.DSCatalogRepository;
import cn.dtkeys.dataservice.repository.DSConnectionRepository;
import cn.dtkeys.dataservice.repository.DSDefinitionRepository;
import cn.dtkeys.dataservice.repository.DSFieldRepository;
import cn.dtkeys.dataservice.repository.DSParamRepository;
import cn.dtkeys.dataservice.repository.DSSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "data-service.resource-protection.max-batch-size=3",
    "data-service.resource-protection.max-result-rows=4",
    "data-service.resource-protection.query-timeout-seconds=10",
    "data-service.resource-protection.federated-query-timeout-seconds=6",
    "data-service.resource-protection.max-concurrent-queries=1"
})
class QueryControllerIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4");

    @Container
    static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>(MYSQL_IMAGE)
        .withDatabaseName("crm")
        .withUsername("test_user")
        .withPassword("test_pass");

    @Autowired
    private MockMvc mockMvc;

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
    private QueryCache queryCache;

    @Autowired
    private DatasourceConnectionManager datasourceConnectionManager;

    @Autowired
    private ResourceProtectionService resourceProtectionService;

    @Autowired
    private ResourceProtectionProperties resourceProtectionProperties;

    @BeforeEach
    void setUp() {
        resourceProtectionProperties.setMaxBatchSize(3);
        resourceProtectionProperties.setMaxResultRows(4);
        resourceProtectionProperties.setQueryTimeoutSeconds(10);
        resourceProtectionProperties.setFederatedQueryTimeoutSeconds(6);
        jdbcTemplate.execute("""
            truncate table ds_cache_policy, ds_field, ds_param, ds_source, ds_catalog, ds_service restart identity cascade
            """);
        jdbcTemplate.execute("truncate table ds_connection restart identity cascade");
        jdbcTemplate.execute("drop table if exists customer_order");
        jdbcTemplate.execute("""
            create table customer_order (
                customer_id bigint not null,
                order_amount numeric(18, 2) not null,
                active boolean not null
            )
            """);
        jdbcTemplate.update("""
            insert into customer_order (customer_id, order_amount, active)
            values (?, ?, ?), (?, ?, ?)
            """, 1001L, new BigDecimal("99.50"), true, 1002L, new BigDecimal("66.00"), false);
        jdbcTemplate.execute("drop table if exists customer_order_limit");
        jdbcTemplate.execute("""
            create table customer_order_limit (
                customer_id bigint not null,
                order_amount numeric(18, 2) not null,
                active boolean not null
            )
            """);
        jdbcTemplate.update("""
            insert into customer_order_limit (customer_id, order_amount, active)
            values (?, ?, ?), (?, ?, ?), (?, ?, ?), (?, ?, ?), (?, ?, ?)
            """, 2001L, new BigDecimal("10.00"), true,
            2002L, new BigDecimal("20.00"), true,
            2003L, new BigDecimal("30.00"), true,
            2004L, new BigDecimal("40.00"), true,
            2005L, new BigDecimal("50.00"), true);
        jdbcTemplate.execute("drop table if exists customer_base");
        jdbcTemplate.execute("""
            create table customer_base (
                customer_id bigint not null,
                customer_name varchar(64) not null,
                active boolean not null
            )
            """);
        jdbcTemplate.update("""
            insert into customer_base (customer_id, customer_name, active)
            values (?, ?, ?), (?, ?, ?), (?, ?, ?)
            """, 3001L, "Alice", true, 3002L, "Bob", true, 3003L, "Carol", false);
        jdbcTemplate.execute("drop table if exists customer_order_ext");
        jdbcTemplate.execute("""
            create table customer_order_ext (
                customer_id bigint not null,
                order_amount numeric(18, 2) not null
            )
            """);
        jdbcTemplate.update("""
            insert into customer_order_ext (customer_id, order_amount)
            values (?, ?), (?, ?)
            """, 3001L, new BigDecimal("128.00"), 3002L, new BigDecimal("256.50"));
        initMysqlFederatedFixtures();
        queryCache.evictByPrefix("data-service:");
        datasourceConnectionManager.clearCache();
    }

    @Test
    void shouldExposeUnifiedQueryApi() throws Exception {
        createMetadata("PUBLISHED");

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "params": {
                        "customerId": 1001,
                        "active": true
                      },
                      "context": {
                        "operator": "system-a"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data[0].customerId").value(1001))
            .andExpect(jsonPath("$.data[0].orderAmount").value(99.50))
            .andExpect(jsonPath("$.data[0].active").value(true))
            .andExpect(jsonPath("$.meta.serviceCode").value("customer_order_query"))
            .andExpect(jsonPath("$.meta.version").value(1))
            .andExpect(jsonPath("$.meta.rowCount").value(1))
            .andExpect(jsonPath("$.meta.cacheHit").value(false))
            .andExpect(jsonPath("$.meta.operator").value("system-a"))
            .andExpect(jsonPath("$.meta.traceId").isNotEmpty());
    }

    @Test
    void shouldHitCacheWhenSameSingleQueryRepeats() throws Exception {
        createMetadata("PUBLISHED", 20, 10, 5, false, false, true);

        String requestBody = """
            {
              "serviceCode": "customer_order_query",
              "params": {
                "customerId": 1001,
                "active": true
              }
            }
            """;

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.cacheHit").value(false))
            .andExpect(jsonPath("$.meta.cacheHitCount").value(0));

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.cacheHit").value(true))
            .andExpect(jsonPath("$.meta.cacheHitCount").value(1));
    }

    @Test
    void shouldRejectServiceWithoutSourceAsConfigInvalid() throws Exception {
        createMetadata("PUBLISHED");
        jdbcTemplate.update("delete from ds_source where service_id = (select id from ds_service where service_code = ?)",
            "customer_order_query");

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "params": {
                        "customerId": 1001,
                        "active": true
                      }
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("SERVICE_CONFIG_INVALID"))
            .andExpect(jsonPath("$.message").value("数据服务未配置查询来源: customer_order_query"));
    }

    @Test
    void shouldUseDifferentCacheKeyForDifferentParams() throws Exception {
        createMetadata("PUBLISHED", 20, 10, 5, false, false, true);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "params": {
                        "customerId": 1001,
                        "active": true
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.cacheHit").value(false));

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "params": {
                        "customerId": 1002,
                        "active": false
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].customerId").value(1002))
            .andExpect(jsonPath("$.meta.cacheHit").value(false))
            .andExpect(jsonPath("$.meta.cacheHitCount").value(0));
    }

    @Test
    void shouldUseDifferentCacheNamespaceForDifferentPublishedVersion() throws Exception {
        createMetadata("PUBLISHED", 20, 10, 5, false, false, true);

        String requestBody = """
            {
              "serviceCode": "customer_order_query",
              "params": {
                "customerId": 1001,
                "active": true
              }
            }
            """;

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.version").value(1))
            .andExpect(jsonPath("$.meta.cacheHit").value(false));

        jdbcTemplate.update("update ds_service set version = 2 where service_code = 'customer_order_query'");

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.version").value(2))
            .andExpect(jsonPath("$.meta.cacheHit").value(false))
            .andExpect(jsonPath("$.meta.cacheHitCount").value(0));
    }

    @Test
    void shouldRejectMissingRequiredParam() throws Exception {
        createMetadata("PUBLISHED");

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "params": {
                        "customerId": 1001
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PARAM_INVALID"))
            .andExpect(jsonPath("$.message").value("缺少必填参数: active"))
            .andExpect(jsonPath("$.meta.traceId").isNotEmpty());
    }

    @Test
    void shouldRejectUnpublishedService() throws Exception {
        createMetadata("DRAFT");

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "params": {
                        "customerId": 1001,
                        "active": true
                      }
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("SERVICE_DISABLED"))
            .andExpect(jsonPath("$.meta.traceId").isNotEmpty());
    }

    @Test
    void shouldRejectBatchParamsBeforeBatchQueryIsImplemented() throws Exception {
        createMetadata("PUBLISHED");

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "batchParams": [
                        {
                          "customerId": 1001,
                          "active": true
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data[0].index").value(0))
            .andExpect(jsonPath("$.data[0].params.customerId").value(1001))
            .andExpect(jsonPath("$.data[0].rowCount").value(1))
            .andExpect(jsonPath("$.data[0].rows[0].customerId").value(1001))
            .andExpect(jsonPath("$.meta.batch").value(true))
            .andExpect(jsonPath("$.meta.batchSize").value(1))
            .andExpect(jsonPath("$.meta.rowCount").value(1));
    }

    @Test
    void shouldRejectUnknownParam() throws Exception {
        createMetadata("PUBLISHED");

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "params": {
                        "customerId": 1001,
                        "active": true,
                        "unexpected": "bad"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PARAM_INVALID"))
            .andExpect(jsonPath("$.message").value("存在未定义参数: unexpected"));
    }

    @Test
    void shouldRejectInvalidBooleanParamType() throws Exception {
        createMetadata("PUBLISHED");

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "params": {
                        "customerId": 1001,
                        "active": "not-bool"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PARAM_INVALID"))
            .andExpect(jsonPath("$.message").value("参数类型不匹配: active 需要 BOOLEAN，实际值=not-bool"));
    }

    @Test
    void shouldSupportBatchQuery() throws Exception {
        createMetadata("PUBLISHED");

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "batchParams": [
                        {
                          "customerId": 1001,
                          "active": true
                        },
                        {
                          "customerId": 1002,
                          "active": false
                        }
                      ],
                      "context": {
                        "operator": "batch-client"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].index").value(0))
            .andExpect(jsonPath("$.data[0].params.customerId").value(1001))
            .andExpect(jsonPath("$.data[0].rowCount").value(1))
            .andExpect(jsonPath("$.data[0].rows[0].customerId").value(1001))
            .andExpect(jsonPath("$.data[1].index").value(1))
            .andExpect(jsonPath("$.data[1].params.customerId").value(1002))
            .andExpect(jsonPath("$.data[1].rowCount").value(1))
            .andExpect(jsonPath("$.data[1].rows[0].customerId").value(1002))
            .andExpect(jsonPath("$.meta.batch").value(true))
            .andExpect(jsonPath("$.meta.batchSize").value(2))
            .andExpect(jsonPath("$.meta.rowCount").value(2))
            .andExpect(jsonPath("$.meta.operator").value("batch-client"));
    }

    @Test
    void shouldSupportPredefinedCrossSourceQuery() throws Exception {
        createPredefinedJoinMetadata("PUBLISHED", false, null, null);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_profile_query",
                      "params": {
                        "active": true,
                        "customerIds": [3001, 3002]
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].customerId").value(3001))
            .andExpect(jsonPath("$.data[0].customerName").value("Alice"))
            .andExpect(jsonPath("$.data[0].active").value(true))
            .andExpect(jsonPath("$.data[0].orderAmount").value(128.00))
            .andExpect(jsonPath("$.data[1].customerId").value(3002))
            .andExpect(jsonPath("$.data[1].customerName").value("Bob"))
            .andExpect(jsonPath("$.data[1].active").value(true))
            .andExpect(jsonPath("$.data[1].orderAmount").value(256.50))
            .andExpect(jsonPath("$.data[0].orderCustomerId").doesNotExist())
            .andExpect(jsonPath("$.meta.serviceCode").value("customer_profile_query"))
            .andExpect(jsonPath("$.meta.rowCount").value(2))
            .andExpect(jsonPath("$.meta.cacheHit").value(false))
            .andExpect(jsonPath("$.meta.sqlType").value("SIMPLE_SQL"))
            .andExpect(jsonPath("$.meta.executionMode").value("REMOTE_PLUS_LOCAL"))
            .andExpect(jsonPath("$.meta.federatedExecution.mode").value("LOOKUP"))
            .andExpect(jsonPath("$.meta.federatedExecution.maxParallelism").value(1))
            .andExpect(jsonPath("$.meta.federatedExecution.stageWaveCount").value(2))
            .andExpect(jsonPath("$.meta.federatedExecution.executedStageCount").value(2))
            .andExpect(jsonPath("$.meta.federatedExecution.stageSummaries.length()").value(2))
            .andExpect(jsonPath("$.meta.federatedExecution.stageSummaries[0].stageId").value("stage-1"))
            .andExpect(jsonPath("$.meta.federatedExecution.stageSummaries[0].lookupDriven").value(false))
            .andExpect(jsonPath("$.meta.federatedExecution.stageSummaries[1].stageId").value("stage-2"))
            .andExpect(jsonPath("$.meta.federatedExecution.stageSummaries[1].lookupDriven").value(true))
            .andExpect(jsonPath("$.meta.federatedExecution.resultBuffer.previewRowCount").value(2));
    }

    @Test
    void shouldExecuteFederatedSqlAcrossSamePostgresqlTypeSources() throws Exception {
        createFederatedSqlMetadata("PUBLISHED", false);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "federated_customer_sql_query",
                      "params": {
                        "customerId": 3001
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].customerId").value(3001))
            .andExpect(jsonPath("$.data[0].customerName").value("Alice"))
            .andExpect(jsonPath("$.data[0].orderAmount").value(128.00))
            .andExpect(jsonPath("$.meta.sqlType").value("FEDERATED_SQL"))
            .andExpect(jsonPath("$.meta.executionMode").value("REMOTE_PLUS_LOCAL"))
            .andExpect(jsonPath("$.meta.federatedExecution.executedStageCount").value(2))
            .andExpect(jsonPath("$.meta.federatedExecution.stageSummaries[0].sourceAlias").value("pg_customer"))
            .andExpect(jsonPath("$.meta.federatedExecution.stageSummaries[1].sourceAlias").value("pg_order"));
    }

    @Test
    void shouldExecuteFederatedSqlAcrossPostgresqlAndMysqlSources() throws Exception {
        createFederatedSqlMetadata("PUBLISHED", true);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "federated_customer_sql_query",
                      "params": {
                        "customerId": 3002
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].customerId").value(3002))
            .andExpect(jsonPath("$.data[0].customerName").value("Bob"))
            .andExpect(jsonPath("$.data[0].orderAmount").value(256.50))
            .andExpect(jsonPath("$.meta.federatedExecution.executedStageCount").value(2))
            .andExpect(jsonPath("$.meta.federatedExecution.resultBuffer.previewRowCount").value(1));
    }

    @Test
    void shouldPreferDatabasePushdownWhenPredefinedJoinUsesSameConnection() throws Exception {
        createPredefinedJoinMetadata("PUBLISHED", true, null, null);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_profile_query",
                      "params": {
                        "active": true,
                        "customerIds": [3001, 3002]
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].customerId").value(3001))
            .andExpect(jsonPath("$.data[0].orderAmount").value(128.00))
            .andExpect(jsonPath("$.data[1].customerId").value(3002))
            .andExpect(jsonPath("$.data[1].orderAmount").value(256.50))
            .andExpect(jsonPath("$.data[0].orderCustomerId").doesNotExist())
            .andExpect(jsonPath("$.data[1].orderCustomerId").doesNotExist())
            .andExpect(jsonPath("$.meta.federatedExecution.mode").value("PUSHDOWN"))
            .andExpect(jsonPath("$.meta.federatedExecution.maxParallelism").value(1))
            .andExpect(jsonPath("$.meta.federatedExecution.stageWaveCount").value(1))
            .andExpect(jsonPath("$.meta.federatedExecution.executedStageCount").value(1))
            .andExpect(jsonPath("$.meta.federatedExecution.stageSummaries.length()").value(1));
    }

    @Test
    void shouldRejectFederatedQueryWhenTimeoutExceedsFederatedSystemLimit() throws Exception {
        createPredefinedJoinMetadata("PUBLISHED", false, 20, null);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_profile_query",
                      "params": {
                        "active": true,
                        "customerIds": [3001, 3002]
                      }
                    }
                    """))
            .andExpect(status().isRequestTimeout())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("QUERY_TIMEOUT"))
            .andExpect(jsonPath("$.message").value("联邦查询超时阈值超过系统限制: 6 秒"));
    }

    @Test
    void shouldExposeSpillSummaryForLargeFederatedLookupResult() throws Exception {
        resourceProtectionProperties.setMaxResultRows(220);
        jdbcTemplate.update("""
            insert into customer_base (customer_id, customer_name, active)
            select gs, 'Customer-' || gs, true
            from generate_series(4000, 4219) gs
            """);
        jdbcTemplate.update("""
            insert into customer_order_ext (customer_id, order_amount)
            select gs, (gs / 10.0)::numeric(18, 2)
            from generate_series(4000, 4219) gs
            """);
        createPredefinedJoinMetadata("PUBLISHED", false, null, 220);
        String customerIds = IntStream.rangeClosed(4000, 4219)
            .mapToObj(String::valueOf)
            .collect(Collectors.joining(", "));

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_profile_query",
                      "params": {
                        "active": true,
                        "customerIds": [%s]
                      }
                    }
                    """.formatted(customerIds)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(200))
            .andExpect(jsonPath("$.meta.rowCount").value(200))
            .andExpect(jsonPath("$.meta.federatedExecution.mode").value("LOOKUP"))
            .andExpect(jsonPath("$.meta.federatedExecution.resultBuffer.totalRowCount").value(220))
            .andExpect(jsonPath("$.meta.federatedExecution.resultBuffer.previewRowCount").value(200))
            .andExpect(jsonPath("$.meta.federatedExecution.resultBuffer.spillTriggered").value(true))
            .andExpect(jsonPath("$.meta.federatedExecution.resultBuffer.spilledRowCount").value(20))
            .andExpect(jsonPath("$.meta.federatedExecution.resultBuffer.spillFile").isNotEmpty());
    }

    @Test
    void shouldExposePerItemCacheStatusForBatchQuery() throws Exception {
        createMetadata("PUBLISHED", 20, 10, 5, false, false, true);

        String requestBody = """
            {
              "serviceCode": "customer_order_query",
              "batchParams": [
                {
                  "customerId": 1001,
                  "active": true
                },
                {
                  "customerId": 1002,
                  "active": false
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].cacheHit").value(false))
            .andExpect(jsonPath("$.data[1].cacheHit").value(false))
            .andExpect(jsonPath("$.meta.cacheHit").value(false))
            .andExpect(jsonPath("$.meta.cacheHitCount").value(0));

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].cacheHit").value(true))
            .andExpect(jsonPath("$.data[1].cacheHit").value(true))
            .andExpect(jsonPath("$.meta.cacheHit").value(true))
            .andExpect(jsonPath("$.meta.cacheHitCount").value(2));
    }

    @Test
    void shouldRejectBatchQueryWhenBatchSizeExceedsServiceLimit() throws Exception {
        createMetadata("PUBLISHED", 2, 10, 5, false, false);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "batchParams": [
                        {
                          "customerId": 1001,
                          "active": true
                        },
                        {
                          "customerId": 1002,
                          "active": false
                        },
                        {
                          "customerId": 1001,
                          "active": true
                        }
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PARAM_INVALID"))
            .andExpect(jsonPath("$.message").value("批量条数超过服务限制: 2"));
    }

    @Test
    void shouldRejectBatchQueryWhenBatchSizeExceedsSystemLimit() throws Exception {
        createMetadata("PUBLISHED", 20, 10, 1, false, false);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "batchParams": [
                        {
                          "customerId": 1001,
                          "active": true
                        },
                        {
                          "customerId": 1002,
                          "active": false
                        },
                        {
                          "customerId": 1001,
                          "active": true
                        },
                        {
                          "customerId": 1002,
                          "active": false
                        }
                      ]
                    }
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("RESOURCE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.message").value("批量规模超过系统限制: 3"));
    }

    @Test
    void shouldRejectSingleQueryWhenResultRowsExceedServiceLimit() throws Exception {
        createMetadata("PUBLISHED", 20, 2, 1, true, false);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "params": {
                        "customerId": 2001,
                        "active": true
                      }
                    }
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("RESOURCE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.message").value("结果集行数超过服务限制: 2"));
    }

    @Test
    void shouldRejectSingleQueryWhenResultRowsExceedSystemLimit() throws Exception {
        createMetadata("PUBLISHED", 20, 0, 1, true, false);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "params": {
                        "customerId": 2001,
                        "active": true
                      }
                    }
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("RESOURCE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.message").value("结果集行数超过系统限制: 4"));
    }

    @Test
    void shouldRejectQueryWhenServiceTimeoutExceedsSystemLimit() throws Exception {
        createMetadata("PUBLISHED", 20, 10, 20, false, false);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "params": {
                        "customerId": 1001,
                        "active": true
                      }
                    }
                    """))
            .andExpect(status().isRequestTimeout())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("QUERY_TIMEOUT"))
            .andExpect(jsonPath("$.message").value("查询超时阈值超过系统限制: 10 秒"));
    }

    @Test
    void shouldApplySystemDefaultsWhenServiceProtectionConfigMissing() throws Exception {
        createMetadata("PUBLISHED", 0, 0, 0, false, false);

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceCode": "customer_order_query",
                      "batchParams": [
                        {
                          "customerId": 1001,
                          "active": true
                        },
                        {
                          "customerId": 1002,
                          "active": false
                        },
                        {
                          "customerId": 1001,
                          "active": true
                        },
                        {
                          "customerId": 1002,
                          "active": false
                        }
                      ]
                    }
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("RESOURCE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.message").value("批量规模超过系统限制: 3"));
    }

    @Test
    void shouldRejectConcurrentQueriesWhenSystemLimitIsReached() throws Exception {
        createMetadata("PUBLISHED", 20, 10, 5, false, true);
        String requestBody = """
            {
              "serviceCode": "customer_order_query",
              "params": {
                "customerId": 1001,
                "active": true
              }
            }
            """;
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger firstStatus = new AtomicInteger();

        Thread first = new Thread(() -> {
            try {
                int statusCode = mockMvc.perform(post("/api/data-services/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                    .andReturn()
                    .getResponse()
                    .getStatus();
                firstStatus.set(statusCode);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            } finally {
                finished.countDown();
            }
        });
        first.start();
        while (resourceProtectionService.availableQuerySlots() != 0) {
            Thread.sleep(20);
        }

        mockMvc.perform(post("/api/data-services/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("RESOURCE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.message").value("并发查询数超过系统限制: 1"));

        finished.await();
        first.join();

        org.assertj.core.api.Assertions.assertThat(firstStatus.get()).isEqualTo(200);
    }

    private void createMetadata(String serviceStatus) {
        createMetadata(serviceStatus, 20, 10, 5, false, false, false);
    }

    private void createMetadata(String serviceStatus,
                                int maxBatchSize,
                                int maxResultRows,
                                int queryTimeoutSeconds,
                                boolean limitQuery,
                                boolean sleepQuery) {
        createMetadata(serviceStatus, maxBatchSize, maxResultRows, queryTimeoutSeconds, limitQuery, sleepQuery, false);
    }

    private void createMetadata(String serviceStatus,
                                int maxBatchSize,
                                int maxResultRows,
                                int queryTimeoutSeconds,
                                boolean limitQuery,
                                boolean sleepQuery,
                                boolean cacheEnabled) {
        DSConnection connection = new DSConnection();
        connection.setConnectionCode("pg_data_service");
        connection.setConnectionName("data_service test");
        connection.setDbType("POSTGRESQL");
        connection.setHost("localhost");
        connection.setPort(5432);
        connection.setUsername("postgres");
        connection.setPasswordCiphertext("postgres");
        connection.setStatus("ENABLED");
        connection.setConnectionConfigJson("{\"database\":\"data_service\"}");
        connection.setDeleted(false);
        connection.setCreatedBy("tester");
        connection.setUpdatedBy("tester");
        dsConnectionRepository.insert(connection);

        DSCatalog catalog = new DSCatalog();
        catalog.setConnectionId(connection.getId());
        catalog.setCatalogType("SCHEMA");
        catalog.setCatalogValue("public");
        catalog.setDeleted(false);
        catalog.setCreatedBy("tester");
        catalog.setUpdatedBy("tester");
        dsCatalogRepository.insert(catalog);

        DSDefinition definition = new DSDefinition();
        definition.setServiceCode("customer_order_query");
        definition.setServiceName("客户订单查询");
        definition.setServiceType("SIMPLE_QUERY");
        definition.setStatus(serviceStatus);
        definition.setSqlTemplate(resolveSqlTemplate(limitQuery, sleepQuery));
        definition.setSqlType("SIMPLE_SQL");
        definition.setExecutionMode("REMOTE_ONLY");
        definition.setPlanStatus("PUBLISHED");
        definition.setVersion(1);
        definition.setMaxBatchSize(maxBatchSize > 0 ? maxBatchSize : null);
        definition.setMaxResultRows(maxResultRows > 0 ? maxResultRows : null);
        definition.setQueryTimeoutSeconds(queryTimeoutSeconds > 0 ? queryTimeoutSeconds : null);
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
        source.setSourceValue("customer_order");
        source.setDeleted(false);
        source.setCreatedBy("tester");
        source.setUpdatedBy("tester");
        dsSourceRepository.insert(source);

        DSParam customerId = new DSParam();
        customerId.setServiceId(definition.getId());
        customerId.setParamName("customerId");
        customerId.setDisplayName("客户号");
        customerId.setParamType("LONG");
        customerId.setSqlPlaceholder("customerId");
        customerId.setRequired(true);
        customerId.setSortOrder(1);
        customerId.setDeleted(false);
        customerId.setCreatedBy("tester");
        customerId.setUpdatedBy("tester");
        dsParamRepository.insert(customerId);

        DSParam active = new DSParam();
        active.setServiceId(definition.getId());
        active.setParamName("active");
        active.setDisplayName("激活状态");
        active.setParamType("BOOLEAN");
        active.setSqlPlaceholder("active");
        active.setRequired(true);
        active.setSortOrder(2);
        active.setDeleted(false);
        active.setCreatedBy("tester");
        active.setUpdatedBy("tester");
        dsParamRepository.insert(active);

        dsFieldRepository.insert(field(definition.getId(), "customer", "customer_id", "customerId", "LONG", 1));
        dsFieldRepository.insert(field(definition.getId(), "customer", "order_amount", "orderAmount", "DECIMAL", 2));
        dsFieldRepository.insert(field(definition.getId(), "customer", "active", "active", "BOOLEAN", 3));

        if (cacheEnabled) {
            jdbcTemplate.update("""
                insert into ds_cache_policy (service_id, enabled, ttl_seconds, cache_key_template, max_entries,
                                             deleted, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, definition.getId(), true, 300, "data-service:{serviceCode}:v{version}:{paramHash}", 100,
                false, "tester", "tester");
        }
    }

    private void createPredefinedJoinMetadata(String serviceStatus,
                                              boolean sameConnection,
                                              Integer federatedQueryTimeoutSeconds,
                                              Integer maxResultRows) {
        DSConnection connection = new DSConnection();
        connection.setConnectionCode("pg_data_service_join");
        connection.setConnectionName("data_service join test");
        connection.setDbType("POSTGRESQL");
        connection.setHost("localhost");
        connection.setPort(5432);
        connection.setUsername("postgres");
        connection.setPasswordCiphertext("postgres");
        connection.setStatus("ENABLED");
        connection.setConnectionConfigJson("{\"database\":\"data_service\"}");
        connection.setDeleted(false);
        connection.setCreatedBy("tester");
        connection.setUpdatedBy("tester");
        dsConnectionRepository.insert(connection);

        DSCatalog catalog = catalog(connection.getId(), "SCHEMA", "public");
        dsCatalogRepository.insert(catalog);

        DSConnection childConnection = connection;
        DSCatalog childCatalog = catalog;
        if (!sameConnection) {
            childConnection = new DSConnection();
            childConnection.setConnectionCode("pg_data_service_join_child");
            childConnection.setConnectionName("data_service join child test");
            childConnection.setDbType("POSTGRESQL");
            childConnection.setHost("localhost");
            childConnection.setPort(5432);
            childConnection.setUsername("postgres");
            childConnection.setPasswordCiphertext("postgres");
            childConnection.setStatus("ENABLED");
            childConnection.setConnectionConfigJson("{\"database\":\"data_service\"}");
            childConnection.setDeleted(false);
            childConnection.setCreatedBy("tester");
            childConnection.setUpdatedBy("tester");
            dsConnectionRepository.insert(childConnection);

            childCatalog = catalog(childConnection.getId(), "SCHEMA", "public");
            dsCatalogRepository.insert(childCatalog);
        }

        DSDefinition definition = new DSDefinition();
        definition.setServiceCode("customer_profile_query");
        definition.setServiceName("客户资料组合查询");
        definition.setServiceType("SIMPLE_QUERY");
        definition.setStatus(serviceStatus);
        definition.setSqlTemplate("""
            select
                customer_id as customer_base_customer_id,
                customer_name as customer_base_customer_name,
                active as customer_base_active
            from customer_base
            where active = /* active */false
              and customer_id in (/* customerIds */(0))
            order by customer_id
            """);
        definition.setSqlType("SIMPLE_SQL");
        definition.setExecutionMode("REMOTE_PLUS_LOCAL");
        definition.setPlanStatus("PUBLISHED");
        definition.setVersion(1);
        definition.setFederatedQueryTimeoutSeconds(federatedQueryTimeoutSeconds);
        definition.setMaxResultRows(maxResultRows);
        definition.setDeleted(false);
        definition.setCreatedBy("tester");
        definition.setUpdatedBy("tester");
        dsDefinitionRepository.insert(definition);

        DSSource primarySource = new DSSource();
        primarySource.setServiceId(definition.getId());
        primarySource.setConnectionId(connection.getId());
        primarySource.setCatalogId(catalog.getId());
        primarySource.setSourceAlias("customer_base");
        primarySource.setSourceType("TABLE");
        primarySource.setSourceValue("customer_base");
        primarySource.setDeleted(false);
        primarySource.setCreatedBy("tester");
        primarySource.setUpdatedBy("tester");
        dsSourceRepository.insert(primarySource);

        DSSource childSource = new DSSource();
        childSource.setServiceId(definition.getId());
        childSource.setConnectionId(childConnection.getId());
        childSource.setCatalogId(childCatalog.getId());
        childSource.setSourceAlias("customer_order_ext");
        childSource.setSourceType("TABLE");
        childSource.setSourceValue("customer_order_ext");
        childSource.setConfigJson("""
            {
              "joinType": "LEFT",
              "lookupParam": "customerIds",
              "lookupSourceColumn": "customer_id",
              "parentJoinField": "customerId",
              "childJoinField": "orderCustomerId",
              "childSqlTemplate": "select customer_id as customer_order_ext_customer_id, order_amount as customer_order_ext_order_amount from customer_order_ext where customer_id in (/* customerIds */(0))"
            }
            """);
        childSource.setDeleted(false);
        childSource.setCreatedBy("tester");
        childSource.setUpdatedBy("tester");
        dsSourceRepository.insert(childSource);

        DSParam active = new DSParam();
        active.setServiceId(definition.getId());
        active.setParamName("active");
        active.setDisplayName("激活状态");
        active.setParamType("BOOLEAN");
        active.setSqlPlaceholder("active");
        active.setRequired(true);
        active.setSortOrder(1);
        active.setDeleted(false);
        active.setCreatedBy("tester");
        active.setUpdatedBy("tester");
        dsParamRepository.insert(active);

        DSParam customerIds = new DSParam();
        customerIds.setServiceId(definition.getId());
        customerIds.setParamName("customerIds");
        customerIds.setDisplayName("客户列表");
        customerIds.setParamType("LIST");
        customerIds.setSqlPlaceholder("customerIds");
        customerIds.setRequired(true);
        customerIds.setSortOrder(2);
        customerIds.setDeleted(false);
        customerIds.setCreatedBy("tester");
        customerIds.setUpdatedBy("tester");
        dsParamRepository.insert(customerIds);

        dsFieldRepository.insert(field(definition.getId(), "customer_base", "customer_id", "customerId", "LONG", 1, true));
        dsFieldRepository.insert(field(definition.getId(), "customer_base", "customer_name", "customerName", "STRING", 2));
        dsFieldRepository.insert(field(definition.getId(), "customer_base", "active", "active", "BOOLEAN", 3));
        dsFieldRepository.insert(field(definition.getId(), "customer_order_ext", "customer_id", "orderCustomerId", "LONG", 4,
            true));
        dsFieldRepository.insert(field(definition.getId(), "customer_order_ext", "order_amount", "orderAmount", "DECIMAL", 5));
    }

    private void createFederatedSqlMetadata(String serviceStatus, boolean useMysqlChild) {
        DSConnection pgConnection = new DSConnection();
        pgConnection.setConnectionCode("fed_pg_primary");
        pgConnection.setConnectionName("fed pg primary");
        pgConnection.setDbType("POSTGRESQL");
        pgConnection.setHost("localhost");
        pgConnection.setPort(5432);
        pgConnection.setUsername("postgres");
        pgConnection.setPasswordCiphertext("postgres");
        pgConnection.setStatus("ENABLED");
        pgConnection.setConnectionConfigJson("{\"database\":\"data_service\"}");
        pgConnection.setDeleted(false);
        pgConnection.setCreatedBy("tester");
        pgConnection.setUpdatedBy("tester");
        dsConnectionRepository.insert(pgConnection);
        DSCatalog pgCatalog = catalog(pgConnection.getId(), "SCHEMA", "public");
        dsCatalogRepository.insert(pgCatalog);

        DSConnection childConnection = pgConnection;
        DSCatalog childCatalog = pgCatalog;
        String childAlias = "pg_order";
        String childTable = "customer_order_ext";
        if (useMysqlChild) {
            childConnection = new DSConnection();
            childConnection.setConnectionCode("fed_mysql_child");
            childConnection.setConnectionName("fed mysql child");
            childConnection.setDbType("MYSQL");
            childConnection.setHost(mysqlContainer.getHost());
            childConnection.setPort(mysqlContainer.getMappedPort(MySQLContainer.MYSQL_PORT));
            childConnection.setUsername(mysqlContainer.getUsername());
            childConnection.setPasswordCiphertext(mysqlContainer.getPassword());
            childConnection.setStatus("ENABLED");
            childConnection.setConnectionConfigJson("""
                {"database":"crm","useSSL":"false","allowPublicKeyRetrieval":"true","serverTimezone":"UTC"}
                """);
            childConnection.setDeleted(false);
            childConnection.setCreatedBy("tester");
            childConnection.setUpdatedBy("tester");
            dsConnectionRepository.insert(childConnection);
            childCatalog = new DSCatalog();
            childCatalog.setConnectionId(childConnection.getId());
            childCatalog.setCatalogType("DATABASE");
            childCatalog.setCatalogValue("crm");
            childCatalog.setDeleted(false);
            childCatalog.setCreatedBy("tester");
            childCatalog.setUpdatedBy("tester");
            dsCatalogRepository.insert(childCatalog);
            childAlias = "mysql_order";
            childTable = "customer_order_ext_mysql";
        }

        DSDefinition definition = new DSDefinition();
        definition.setServiceCode("federated_customer_sql_query");
        definition.setServiceName("联邦客户 SQL 查询");
        definition.setServiceType("FEDERATED_QUERY");
        definition.setStatus(serviceStatus);
        definition.setSqlTemplate("""
            select pg_customer.customer_id, pg_customer.customer_name, %s.order_amount
            from pg_customer
            join %s on pg_customer.customer_id = %s.customer_id
            where pg_customer.customer_id = /* customerId */0
            """.formatted(childAlias, childAlias, childAlias));
        definition.setSqlType("FEDERATED_SQL");
        definition.setExecutionMode("REMOTE_PLUS_LOCAL");
        definition.setPlanStatus("PUBLISHED");
        definition.setVersion(1);
        definition.setFederatedQueryTimeoutSeconds(6);
        definition.setMaxResultRows(4);
        definition.setDeleted(false);
        definition.setCreatedBy("tester");
        definition.setUpdatedBy("tester");
        dsDefinitionRepository.insert(definition);

        DSSource primarySource = new DSSource();
        primarySource.setServiceId(definition.getId());
        primarySource.setConnectionId(pgConnection.getId());
        primarySource.setCatalogId(pgCatalog.getId());
        primarySource.setSourceAlias("pg_customer");
        primarySource.setSourceType("TABLE");
        primarySource.setSourceValue("customer_base");
        primarySource.setJoinKey("customer_id");
        primarySource.setDeleted(false);
        primarySource.setCreatedBy("tester");
        primarySource.setUpdatedBy("tester");
        dsSourceRepository.insert(primarySource);

        DSSource childSource = new DSSource();
        childSource.setServiceId(definition.getId());
        childSource.setConnectionId(childConnection.getId());
        childSource.setCatalogId(childCatalog.getId());
        childSource.setSourceAlias(childAlias);
        childSource.setSourceType("TABLE");
        childSource.setSourceValue(childTable);
        childSource.setJoinKey("customer_id");
        childSource.setDeleted(false);
        childSource.setCreatedBy("tester");
        childSource.setUpdatedBy("tester");
        dsSourceRepository.insert(childSource);

        DSParam customerId = new DSParam();
        customerId.setServiceId(definition.getId());
        customerId.setParamName("customerId");
        customerId.setDisplayName("客户号");
        customerId.setParamType("LONG");
        customerId.setSqlPlaceholder("customerId");
        customerId.setRequired(true);
        customerId.setSortOrder(1);
        customerId.setDeleted(false);
        customerId.setCreatedBy("tester");
        customerId.setUpdatedBy("tester");
        dsParamRepository.insert(customerId);

        dsFieldRepository.insert(field(definition.getId(), "pg_customer", "customer_id", "customerId", "LONG", 1, true));
        dsFieldRepository.insert(field(definition.getId(), "pg_customer", "customer_name", "customerName", "STRING", 2));
        dsFieldRepository.insert(field(definition.getId(), childAlias, "customer_id", "orderCustomerId", "LONG", 3, true));
        dsFieldRepository.insert(field(definition.getId(), childAlias, "order_amount", "orderAmount", "DECIMAL", 4));
    }

    private DSCatalog catalog(Long connectionId, String catalogType, String catalogValue) {
        DSCatalog catalog = new DSCatalog();
        catalog.setConnectionId(connectionId);
        catalog.setCatalogType(catalogType);
        catalog.setCatalogValue(catalogValue);
        catalog.setDeleted(false);
        catalog.setCreatedBy("tester");
        catalog.setUpdatedBy("tester");
        return catalog;
    }

    private String resolveSqlTemplate(boolean limitQuery, boolean sleepQuery) {
        if (sleepQuery) {
            return """
                select
                    customer_id as customer_customer_id,
                    order_amount as customer_order_amount,
                    active as customer_active
                from customer_order
                where customer_id = /* customerId */0
                  and active = /* active */false
                  and pg_sleep(3) is null
                order by customer_id
                """;
        }
        if (limitQuery) {
            return """
                select
                    customer_id as customer_customer_id,
                    order_amount as customer_order_amount,
                    active as customer_active
                from customer_order_limit
                where customer_id >= /* customerId */0
                  and active = /* active */false
                order by customer_id
                """;
        }
        return """
            select
                customer_id as customer_customer_id,
                order_amount as customer_order_amount,
                active as customer_active
            from customer_order
            where customer_id = /* customerId */0
              and active = /* active */false
            order by customer_id
            """;
    }

    private void initMysqlFederatedFixtures() {
        try {
            try (java.sql.Connection connection = java.sql.DriverManager.getConnection(
                mysqlContainer.getJdbcUrl(),
                mysqlContainer.getUsername(),
                mysqlContainer.getPassword()
            );
                 java.sql.Statement statement = connection.createStatement()) {
                statement.execute("drop table if exists customer_order_ext_mysql");
                statement.execute("""
                    create table customer_order_ext_mysql (
                        customer_id bigint not null,
                        order_amount decimal(18, 2) not null
                    )
                    """);
                statement.execute("insert into customer_order_ext_mysql (customer_id, order_amount) values (3001, 128.00), (3002, 256.50)");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("初始化 MySQL 联邦测试数据失败", exception);
        }
    }

    private DSField field(Long serviceId, String alias, String sourceColumn, String fieldName, String fieldType,
                          int sortOrder) {
        return field(serviceId, alias, sourceColumn, fieldName, fieldType, sortOrder, false);
    }

    private DSField field(Long serviceId, String alias, String sourceColumn, String fieldName, String fieldType,
                          int sortOrder, boolean joinKey) {
        DSField field = new DSField();
        field.setServiceId(serviceId);
        field.setSourceAlias(alias);
        field.setSourceColumn(sourceColumn);
        field.setFieldName(fieldName);
        field.setDisplayName(fieldName);
        field.setFieldType(fieldType);
        field.setSortOrder(sortOrder);
        field.setPrimaryKey(false);
        field.setJoinKey(joinKey);
        field.setDeleted(false);
        field.setCreatedBy("tester");
        field.setUpdatedBy("tester");
        return field;
    }
}
