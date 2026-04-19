package cn.dtkeys.dataservice.app;

import cn.dtkeys.dataservice.query.ServiceDefinitionLoader;
import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.service.model.DSDefinition;
import cn.dtkeys.dataservice.service.model.DSField;
import cn.dtkeys.dataservice.service.model.DSParam;
import cn.dtkeys.dataservice.service.model.DSServiceVersion;
import cn.dtkeys.dataservice.service.model.DSSource;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeDefinition;
import cn.dtkeys.dataservice.query.executor.BoundQuery;
import cn.dtkeys.dataservice.query.executor.NamedParameterQueryExecutor;
import cn.dtkeys.dataservice.query.executor.QueryParameterBinder;
import cn.dtkeys.dataservice.query.executor.QueryResultMapper;
import cn.dtkeys.dataservice.query.executor.SqlReadOnlyValidator;
import cn.dtkeys.dataservice.repository.DSCatalogRepository;
import cn.dtkeys.dataservice.repository.DSConnectionRepository;
import cn.dtkeys.dataservice.repository.DSDefinitionRepository;
import cn.dtkeys.dataservice.repository.DSFieldRepository;
import cn.dtkeys.dataservice.repository.DSParamRepository;
import cn.dtkeys.dataservice.repository.DSServiceVersionRepository;
import cn.dtkeys.dataservice.repository.DSSourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class QueryExecutionComponentsIntegrationTest {

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
    private DSServiceVersionRepository dsServiceVersionRepository;

    @Autowired
    private ServiceDefinitionLoader serviceDefinitionLoader;

    @Autowired
    private SqlReadOnlyValidator sqlReadOnlyValidator;

    @Autowired
    private QueryParameterBinder queryParameterBinder;

    @Autowired
    private NamedParameterQueryExecutor namedParameterQueryExecutor;

    @Autowired
    private QueryResultMapper queryResultMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
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
    }

    @Test
    void shouldLoadPublishedServiceAndExecuteReadOnlyQuery() {
        createMetadata();

        DataServiceRuntimeDefinition runtimeDefinition = serviceDefinitionLoader.loadPublished("customer_order_query");
        sqlReadOnlyValidator.validate(runtimeDefinition.definition().getSqlTemplate());
        BoundQuery boundQuery = queryParameterBinder.bind(
            runtimeDefinition.definition().getSqlTemplate(),
            runtimeDefinition.params(),
            Map.of("customerId", 1001L, "active", true)
        );

        List<Map<String, Object>> rawRows = namedParameterQueryExecutor.query(
            runtimeDefinition.sources().get(0),
            boundQuery.sql(),
            boundQuery.params(),
            runtimeDefinition.definition().getQueryTimeoutSeconds(),
            runtimeDefinition.definition().getMaxResultRows()
        );
        List<Map<String, Object>> mappedRows = queryResultMapper.map(rawRows, runtimeDefinition.fields());

        assertThat(mappedRows).containsExactly(Map.of(
            "customerId", 1001L,
            "orderAmount", new BigDecimal("99.50"),
            "active", true
        ));
    }

    @Test
    void shouldRejectNonReadOnlyPublishedQuery() {
        createMetadata();
        jdbcTemplate.update("update ds_service set sql_template = ? where service_code = ?", "delete from customer_order",
            "customer_order_query");

        DataServiceRuntimeDefinition runtimeDefinition = serviceDefinitionLoader.loadPublished("customer_order_query");

        assertThatThrownBy(() -> sqlReadOnlyValidator.validate(runtimeDefinition.definition().getSqlTemplate()))
            .isInstanceOf(ParamInvalidException.class);
    }

    @Test
    void shouldLoadPublishedSnapshotInsteadOfDraftMutations() throws Exception {
        createMetadata();
        DSDefinition definition = dsDefinitionRepository.findByServiceCode("customer_order_query");
        List<DSSource> sources = dsSourceRepository.findByServiceId(definition.getId());
        List<DSParam> params = dsParamRepository.findByServiceId(definition.getId());
        List<DSField> fields = dsFieldRepository.findByServiceId(definition.getId());

        DSServiceVersion serviceVersion = new DSServiceVersion();
        serviceVersion.setServiceId(definition.getId());
        serviceVersion.setVersion(1);
        serviceVersion.setStatus("PUBLISHED");
        serviceVersion.setServiceDefinitionJson(objectMapper.writeValueAsString(definition));
        serviceVersion.setSourceDefinitionJson(objectMapper.writeValueAsString(sources));
        serviceVersion.setParamDefinitionJson(objectMapper.writeValueAsString(params));
        serviceVersion.setFieldDefinitionJson(objectMapper.writeValueAsString(fields));
        serviceVersion.setSqlDefinitionJson("""
            {"sqlTemplate":"select customer_id as customer_customer_id, order_amount as customer_order_amount, active as customer_active from customer_order where customer_id = /* customerId */0 and active = /* active */false order by customer_id","sqlType":"SIMPLE_SQL","executionMode":"REMOTE_ONLY","planStatus":"PUBLISHED"}
            """);
        serviceVersion.setCreatedBy("tester");
        dsServiceVersionRepository.insert(serviceVersion);

        jdbcTemplate.update("""
            update ds_service
            set sql_template = 'select customer_id as customer_customer_id, order_amount as customer_order_amount, active as customer_active from customer_order where customer_id = 1002 and active = false',
                sql_type = 'FEDERATED_SQL',
                execution_mode = 'REMOTE_PLUS_LOCAL',
                plan_status = 'PLANNED'
            where service_code = 'customer_order_query'
            """);

        DataServiceRuntimeDefinition runtimeDefinition = serviceDefinitionLoader.loadPublished("customer_order_query");
        BoundQuery boundQuery = queryParameterBinder.bind(
            runtimeDefinition.definition().getSqlTemplate(),
            runtimeDefinition.params(),
            Map.of("customerId", 1001L, "active", true)
        );

        List<Map<String, Object>> rawRows = namedParameterQueryExecutor.query(
            runtimeDefinition.sources().get(0),
            boundQuery.sql(),
            boundQuery.params(),
            runtimeDefinition.definition().getQueryTimeoutSeconds(),
            runtimeDefinition.definition().getMaxResultRows()
        );
        List<Map<String, Object>> mappedRows = queryResultMapper.map(rawRows, runtimeDefinition.fields());

        assertThat(runtimeDefinition.definition().getSqlType()).isEqualTo("SIMPLE_SQL");
        assertThat(runtimeDefinition.definition().getExecutionMode()).isEqualTo("REMOTE_ONLY");
        assertThat(runtimeDefinition.definition().getPlanStatus()).isEqualTo("PUBLISHED");
        assertThat(mappedRows).containsExactly(Map.of(
            "customerId", 1001L,
            "orderAmount", new BigDecimal("99.50"),
            "active", true
        ));
    }

    private void createMetadata() {
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
        catalog.setCatalogCode("public_schema");
        catalog.setCatalogName("public");
        catalog.setCatalogType("SCHEMA");
        catalog.setCatalogValue("public");
        catalog.setStatus("ENABLED");
        catalog.setDeleted(false);
        catalog.setCreatedBy("tester");
        catalog.setUpdatedBy("tester");
        dsCatalogRepository.insert(catalog);

        DSDefinition definition = new DSDefinition();
        definition.setServiceCode("customer_order_query");
        definition.setServiceName("客户订单查询");
        definition.setServiceType("SIMPLE_QUERY");
        definition.setStatus("PUBLISHED");
        definition.setSqlTemplate("""
            select
                customer_id as customer_customer_id,
                order_amount as customer_order_amount,
                active as customer_active
            from customer_order
            where customer_id = /* customerId */0
              and active = /* active */false
            order by customer_id
            """);
        definition.setSqlType("SIMPLE_SQL");
        definition.setExecutionMode("REMOTE_ONLY");
        definition.setPlanStatus("PUBLISHED");
        definition.setVersion(1);
        definition.setMaxResultRows(10);
        definition.setQueryTimeoutSeconds(5);
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
        source.setStatus("ENABLED");
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
    }

    private DSField field(Long serviceId, String alias, String sourceColumn, String fieldName, String fieldType,
                          int sortOrder) {
        DSField field = new DSField();
        field.setServiceId(serviceId);
        field.setSourceAlias(alias);
        field.setSourceColumn(sourceColumn);
        field.setFieldName(fieldName);
        field.setDisplayName(fieldName);
        field.setFieldType(fieldType);
        field.setSortOrder(sortOrder);
        field.setPrimaryKey(false);
        field.setJoinKey(false);
        field.setDeleted(false);
        field.setCreatedBy("tester");
        field.setUpdatedBy("tester");
        return field;
    }
}
