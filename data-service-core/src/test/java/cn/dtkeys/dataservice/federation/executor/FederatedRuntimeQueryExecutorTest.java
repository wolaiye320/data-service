package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;
import cn.dtkeys.dataservice.query.executor.BoundQuery;
import cn.dtkeys.dataservice.query.executor.NamedParameterQueryExecutor;
import cn.dtkeys.dataservice.query.executor.QueryParameterBinder;
import cn.dtkeys.dataservice.query.executor.QueryResultMapper;
import cn.dtkeys.dataservice.query.executor.SqlReadOnlyValidator;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeDefinition;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeSource;
import cn.dtkeys.dataservice.service.model.DSDefinition;
import cn.dtkeys.dataservice.service.model.DSField;
import cn.dtkeys.dataservice.service.model.DSParam;
import cn.dtkeys.dataservice.service.model.DSSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederatedRuntimeQueryExecutorTest {

    @Mock
    private QueryParameterBinder queryParameterBinder;

    @Mock
    private NamedParameterQueryExecutor namedParameterQueryExecutor;

    @Mock
    private QueryResultMapper queryResultMapper;

    @Mock
    private SqlReadOnlyValidator sqlReadOnlyValidator;

    private FederatedRuntimeQueryExecutor federatedRuntimeQueryExecutor;

    @BeforeEach
    void setUp() {
        federatedRuntimeQueryExecutor = new FederatedRuntimeQueryExecutor(
            queryParameterBinder,
            namedParameterQueryExecutor,
            queryResultMapper,
            sqlReadOnlyValidator
        );
    }

    @Test
    void shouldUseSourceJoinKeyEvenWhenNotExposedAsOutputField() {
        DataServiceRuntimeDefinition runtimeDefinition = runtimeDefinitionWithoutProjectedJoinField();
        FederatedParsedQuery parsedQuery = new FederatedParsedQuery(
            """
                select pg_customer.customer_name, mysql_order.order_amount
                from pg_customer
                join mysql_order on pg_customer.customer_code = mysql_order.customer_code
                where pg_customer.customer_code = /* customerCode */''
                """.trim(),
            List.of("pg_customer.customer_name", "mysql_order.order_amount"),
            List.of("pg_customer", "mysql_order"),
            "pg_customer.customer_code = /* customerCode */''",
            true
        );
        FederatedPlan plan = new FederatedPlan(
            parsedQuery.originalSql(),
            List.of(
                new FederatedPlanStage("stage-1", "pg_customer", "POSTGRESQL", "", List.of(), "", true, List.of(), false,
                    "customer_code", 1, Map.of()),
                new FederatedPlanStage("stage-2", "mysql_order", "MYSQL", "", List.of(), "", true, List.of("stage-1"), true,
                    "customer_code", 2, Map.of())
            ),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            "pg_customer,mysql_order",
            Map.of()
        );

        when(queryParameterBinder.bind(anyString(), any(), anyMap())).thenAnswer(invocation ->
            new BoundQuery(invocation.getArgument(0), invocation.getArgument(2))
        );
        when(namedParameterQueryExecutor.query(any(), anyString(), anyMap(), anyInt(), anyInt()))
            .thenReturn(List.of(Map.of(
                "pg_customer_customer_name", "Alice",
                "pg_customer_customer_code", "C1001"
            )))
            .thenReturn(List.of(Map.of(
                "mysql_order_order_amount", 128.00,
                "mysql_order_customer_code", "C1001"
            )));
        when(queryResultMapper.map(any(), any()))
            .thenReturn(List.of(Map.of(
                "customerName", "Alice",
                "customer_code", "C1001"
            )))
            .thenReturn(List.of(Map.of(
                "orderAmount", 128.00,
                "customer_code", "C1001"
            )));

        FederatedRuntimeQueryExecutor.FederatedRuntimeExecutionResult result = federatedRuntimeQueryExecutor.execute(
            runtimeDefinition,
            parsedQuery,
            plan,
            Map.of("customerCode", "C1001"),
            10,
            20
        );

        assertThat(result.rows()).containsExactly(Map.of(
            "customerName", "Alice",
            "orderAmount", 128.00
        ));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryParameterBinder, times(2)).bind(sqlCaptor.capture(), any(), anyMap());
        assertThat(sqlCaptor.getAllValues().get(1)).contains("customer_code in (/* federatedLookupValues */(0))");
    }

    @Test
    void shouldQualifyStageSourceWithCatalogContext() {
        DataServiceRuntimeDefinition runtimeDefinition = runtimeDefinitionWithCatalogQualifiedSources();
        FederatedParsedQuery parsedQuery = new FederatedParsedQuery(
            """
                select pg_customer.customer_code, pg_customer.customer_name, mysql_order.order_amount
                from pg_customer
                join mysql_order on pg_customer.customer_code = mysql_order.customer_code
                where pg_customer.customer_code = /* customerCode */''
                """.trim(),
            List.of("pg_customer.customer_code", "pg_customer.customer_name", "mysql_order.order_amount"),
            List.of("pg_customer", "mysql_order"),
            "pg_customer.customer_code = /* customerCode */''",
            true
        );
        FederatedPlan plan = new FederatedPlan(
            parsedQuery.originalSql(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            "pg_customer,mysql_order",
            Map.of()
        );

        when(queryParameterBinder.bind(anyString(), any(), anyMap())).thenAnswer(invocation ->
            new BoundQuery(invocation.getArgument(0), invocation.getArgument(2))
        );
        when(namedParameterQueryExecutor.query(any(), anyString(), anyMap(), anyInt(), anyInt()))
            .thenReturn(List.of(Map.of(
                "pg_customer_customer_code", "C1001",
                "pg_customer_customer_name", "Alice"
            )))
            .thenReturn(List.of(Map.of(
                "mysql_order_customer_code", "C1001",
                "mysql_order_order_amount", 128.00
            )));
        when(queryResultMapper.map(any(), any()))
            .thenReturn(List.of(Map.of(
                "customerCode", "C1001",
                "customerName", "Alice"
            )))
            .thenReturn(List.of(Map.of(
                "customer_code", "C1001",
                "orderAmount", 128.00
            )));

        federatedRuntimeQueryExecutor.execute(
            runtimeDefinition,
            parsedQuery,
            plan,
            Map.of("customerCode", "C1001"),
            10,
            20
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(namedParameterQueryExecutor, times(2)).query(any(), sqlCaptor.capture(), anyMap(), anyInt(), anyInt());
        assertThat(sqlCaptor.getAllValues().get(0)).contains(" from ist.fed_test_customer ");
        assertThat(sqlCaptor.getAllValues().get(1)).contains(" from bcs.fed_test_order ");
    }

    private DataServiceRuntimeDefinition runtimeDefinitionWithoutProjectedJoinField() {
        DSDefinition definition = new DSDefinition();
        definition.setServiceCode("federated_bankdb_bcs_test");
        definition.setSqlTemplate("""
            select pg_customer.customer_name, mysql_order.order_amount
            from pg_customer
            join mysql_order on pg_customer.customer_code = mysql_order.customer_code
            where pg_customer.customer_code = /* customerCode */''
            """);

        return new DataServiceRuntimeDefinition(
            definition,
            List.of(runtimeSource("pg_customer", "fed_test_customer", "customer_code", "POSTGRESQL"),
                runtimeSource("mysql_order", "fed_test_order", "customer_code", "MYSQL")),
            List.of(param("customerCode", "STRING")),
            List.of(
                field("pg_customer", "customer_name", "customerName", "STRING", 1),
                field("mysql_order", "order_amount", "orderAmount", "DECIMAL", 2)
            ),
            null
        );
    }

    private DataServiceRuntimeDefinition runtimeDefinitionWithCatalogQualifiedSources() {
        DSDefinition definition = new DSDefinition();
        definition.setServiceCode("federated_bankdb_bcs_test");
        definition.setSqlTemplate("""
            select pg_customer.customer_code, pg_customer.customer_name, mysql_order.order_amount
            from pg_customer
            join mysql_order on pg_customer.customer_code = mysql_order.customer_code
            where pg_customer.customer_code = /* customerCode */''
            """);

        return new DataServiceRuntimeDefinition(
            definition,
            List.of(
                runtimeSource("pg_customer", "fed_test_customer", "customer_code", "POSTGRESQL", "SCHEMA", "ist"),
                runtimeSource("mysql_order", "fed_test_order", "customer_code", "MYSQL", "DATABASE", "bcs")
            ),
            List.of(param("customerCode", "STRING")),
            List.of(
                field("pg_customer", "customer_code", "customerCode", "STRING", 1),
                field("pg_customer", "customer_name", "customerName", "STRING", 2),
                field("mysql_order", "order_amount", "orderAmount", "DECIMAL", 3)
            ),
            null
        );
    }

    private DataServiceRuntimeSource runtimeSource(String alias, String sourceValue, String joinKey, String dbType) {
        return runtimeSource(alias, sourceValue, joinKey, dbType, null, null);
    }

    private DataServiceRuntimeSource runtimeSource(String alias,
                                                   String sourceValue,
                                                   String joinKey,
                                                   String dbType,
                                                   String catalogType,
                                                   String catalogValue) {
        DSSource source = new DSSource();
        source.setSourceAlias(alias);
        source.setSourceValue(sourceValue);
        source.setJoinKey(joinKey);

        DSConnection connection = new DSConnection();
        connection.setDbType(dbType);
        connection.setStatus("ENABLED");

        DSCatalog catalog = new DSCatalog();
        catalog.setCatalogType(catalogType);
        catalog.setCatalogValue(catalogValue);
        return new DataServiceRuntimeSource(source, connection, catalog);
    }

    private DSParam param(String name, String type) {
        DSParam param = new DSParam();
        param.setParamName(name);
        param.setSqlPlaceholder(name);
        param.setParamType(type);
        param.setRequired(true);
        param.setSortOrder(1);
        return param;
    }

    private DSField field(String sourceAlias, String sourceColumn, String fieldName, String fieldType, int sortOrder) {
        DSField field = new DSField();
        field.setSourceAlias(sourceAlias);
        field.setSourceColumn(sourceColumn);
        field.setFieldName(fieldName);
        field.setDisplayName(fieldName);
        field.setFieldType(fieldType);
        field.setSortOrder(sortOrder);
        return field;
    }
}
