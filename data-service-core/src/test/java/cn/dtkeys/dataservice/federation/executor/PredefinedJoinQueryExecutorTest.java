package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.service.model.DSDefinition;
import cn.dtkeys.dataservice.service.model.DSField;
import cn.dtkeys.dataservice.service.model.DSParam;
import cn.dtkeys.dataservice.service.model.DSSource;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeDefinition;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeSource;
import cn.dtkeys.dataservice.query.executor.NamedParameterQueryExecutor;
import cn.dtkeys.dataservice.query.executor.QueryParameterBinder;
import cn.dtkeys.dataservice.query.executor.QueryResultMapper;
import cn.dtkeys.dataservice.query.executor.SqlReadOnlyValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredefinedJoinQueryExecutorTest {

    @Mock
    private QueryParameterBinder queryParameterBinder;

    @Mock
    private NamedParameterQueryExecutor namedParameterQueryExecutor;

    @Mock
    private QueryResultMapper queryResultMapper;

    @Mock
    private SqlReadOnlyValidator sqlReadOnlyValidator;

    @Mock
    private PredefinedJoinResultAssembler predefinedJoinResultAssembler;

    private PredefinedJoinQueryExecutor predefinedJoinQueryExecutor;

    @BeforeEach
    void setUp() {
        predefinedJoinQueryExecutor = new PredefinedJoinQueryExecutor(
            queryParameterBinder,
            namedParameterQueryExecutor,
            queryResultMapper,
            sqlReadOnlyValidator,
            new PredefinedJoinConfigParser(new ObjectMapper()),
            predefinedJoinResultAssembler
        );
    }

    @Test
    void shouldPushDownJoinWhenSourcesShareSameConnection() {
        DataServiceRuntimeDefinition runtimeDefinition = buildRuntimeDefinition(1L, 1L, true);
        when(queryParameterBinder.bind(anyString(), any(), anyMap())).thenAnswer(invocation ->
            new cn.dtkeys.dataservice.query.executor.BoundQuery(
                invocation.getArgument(0),
                invocation.getArgument(2)
            )
        );
        when(namedParameterQueryExecutor.query(any(), anyString(), anyMap(), anyInt(), anyInt()))
            .thenReturn(List.of(Map.of(
                "customer_base_customer_id", 3001L,
                "customer_base_customer_name", "Alice",
                "customer_base_active", true,
                "customer_order_ext_order_amount", 128.00
            )));
        when(queryResultMapper.map(any(), any())).thenReturn(List.of(Map.of(
            "customerId", 3001L,
            "customerName", "Alice",
            "active", true,
            "orderAmount", 128.00
        )));

        List<Map<String, Object>> rows = predefinedJoinQueryExecutor.execute(
            runtimeDefinition,
            Map.of("active", true, "customerIds", List.of(3001L)),
            10,
            20
        );

        assertThat(rows).hasSize(1);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(namedParameterQueryExecutor).query(any(), sqlCaptor.capture(), anyMap(), anyInt(), anyInt());
        assertThat(sqlCaptor.getValue()).contains("left join");
        assertThat(sqlCaptor.getValue()).contains("from (");
        verify(predefinedJoinResultAssembler, never()).assemble(any(), any(), any());
        verify(namedParameterQueryExecutor, times(1)).query(any(), anyString(), anyMap(), anyInt(), anyInt());
    }

    @Test
    void shouldFallbackToLookupAssemblyWhenSourcesUseDifferentConnections() {
        DataServiceRuntimeDefinition runtimeDefinition = buildRuntimeDefinition(1L, 2L, true);
        when(queryParameterBinder.bind(anyString(), any(), anyMap())).thenAnswer(invocation ->
            new cn.dtkeys.dataservice.query.executor.BoundQuery(
                invocation.getArgument(0),
                invocation.getArgument(2)
            )
        );
        when(namedParameterQueryExecutor.query(any(), anyString(), anyMap(), anyInt(), anyInt()))
            .thenReturn(List.of(Map.of(
                "customer_base_customer_id", 3001L,
                "customer_base_customer_name", "Alice",
                "customer_base_active", true
            )));
        doAnswer(invocation -> {
            cn.dtkeys.dataservice.query.executor.StreamingQueryRowHandler handler = invocation.getArgument(6);
            handler.handleRow(Map.of(
                "customer_order_ext_customer_id", 3001L,
                "customer_order_ext_order_amount", 128.00
            ));
            return 1;
        }).when(namedParameterQueryExecutor).streamQuery(any(), anyString(), anyMap(), anyInt(), anyInt(), anyInt(), any());
        when(queryResultMapper.map(any(), any()))
            .thenReturn(List.of(Map.of("customerId", 3001L, "customerName", "Alice", "active", true)))
            .thenReturn(List.of(Map.of("orderCustomerId", 3001L, "orderAmount", 128.00)));
        when(predefinedJoinResultAssembler.assemble(any(), any(), any()))
            .thenReturn(List.of(Map.of("customerId", 3001L, "customerName", "Alice", "active", true, "orderAmount", 128.00)));

        List<Map<String, Object>> rows = predefinedJoinQueryExecutor.execute(
            runtimeDefinition,
            Map.of("active", true, "customerIds", List.of(3001L)),
            10,
            20
        );

        assertThat(rows).hasSize(1);
        verify(namedParameterQueryExecutor, times(1)).query(any(), anyString(), anyMap(), anyInt(), anyInt());
        verify(namedParameterQueryExecutor, times(1)).streamQuery(any(), anyString(), anyMap(), anyInt(), anyInt(), anyInt(), any());
        verify(predefinedJoinResultAssembler).assemble(any(), any(), any());
    }

    @Test
    void shouldReturnLookupExecutionSummary() {
        DataServiceRuntimeDefinition runtimeDefinition = buildRuntimeDefinition(1L, 2L, true);
        when(queryParameterBinder.bind(anyString(), any(), anyMap())).thenAnswer(invocation ->
            new cn.dtkeys.dataservice.query.executor.BoundQuery(
                invocation.getArgument(0),
                invocation.getArgument(2)
            )
        );
        when(namedParameterQueryExecutor.query(any(), anyString(), anyMap(), anyInt(), anyInt()))
            .thenReturn(List.of(Map.of(
                "customer_base_customer_id", 3001L,
                "customer_base_customer_name", "Alice",
                "customer_base_active", true
            )));
        doAnswer(invocation -> {
            cn.dtkeys.dataservice.query.executor.StreamingQueryRowHandler handler = invocation.getArgument(6);
            handler.handleRow(Map.of(
                "customer_order_ext_customer_id", 3001L,
                "customer_order_ext_order_amount", 128.00
            ));
            return 1;
        }).when(namedParameterQueryExecutor).streamQuery(any(), anyString(), anyMap(), anyInt(), anyInt(), anyInt(), any());
        when(queryResultMapper.map(any(), any()))
            .thenReturn(List.of(Map.of("customerId", 3001L, "customerName", "Alice", "active", true)))
            .thenReturn(List.of(Map.of("orderCustomerId", 3001L, "orderAmount", 128.00)));
        when(predefinedJoinResultAssembler.assemble(any(), any(), any()))
            .thenReturn(List.of(Map.of("customerId", 3001L, "customerName", "Alice", "active", true, "orderAmount", 128.00)));

        PredefinedJoinQueryExecutor.PredefinedJoinExecutionResult result = predefinedJoinQueryExecutor.executeWithSummary(
            runtimeDefinition,
            Map.of("active", true, "customerIds", List.of(3001L)),
            10,
            20
        );

        assertThat(result.rows()).hasSize(1);
        assertThat(result.summary()).containsEntry("mode", "LOOKUP");
        assertThat(result.summary()).containsKey("resultBuffer");
    }

    @Test
    void shouldRejectWhenJoinKeyFieldIsNotExplicitlyMarked() {
        DataServiceRuntimeDefinition runtimeDefinition = buildRuntimeDefinition(1L, 1L, false);

        assertThatThrownBy(() -> predefinedJoinQueryExecutor.execute(
            runtimeDefinition,
            Map.of("active", true, "customerIds", List.of(3001L)),
            10,
            20
        ))
            .isInstanceOf(ServiceConfigInvalidException.class)
            .hasMessageContaining("joinKey");
    }

    private DataServiceRuntimeDefinition buildRuntimeDefinition(Long primaryConnectionId,
                                                                Long childConnectionId,
                                                                boolean explicitJoinKey) {
        DSDefinition definition = new DSDefinition();
        definition.setServiceCode("customer_profile_query");
        definition.setVersion(1);
        definition.setSqlTemplate("""
            select
                customer_id as customer_base_customer_id,
                customer_name as customer_base_customer_name,
                active as customer_base_active
            from customer_base
            where active = /* active */false
              and customer_id in (/* customerIds */(0))
            """);

        return new DataServiceRuntimeDefinition(
            definition,
            List.of(runtimeSource(primaryConnectionId, "customer_base"), runtimeSource(childConnectionId, "customer_order_ext")),
            List.of(param("active", "BOOLEAN", "active"), param("customerIds", "LIST", "customerIds")),
            List.of(
                field("customer_base", "customer_id", "customerId", "LONG", 1, explicitJoinKey),
                field("customer_base", "customer_name", "customerName", "STRING", 2, false),
                field("customer_base", "active", "active", "BOOLEAN", 3, false),
                field("customer_order_ext", "customer_id", "orderCustomerId", "LONG", 4, explicitJoinKey),
                field("customer_order_ext", "order_amount", "orderAmount", "DECIMAL", 5, false)
            ),
            null
        );
    }

    private DataServiceRuntimeSource runtimeSource(Long connectionId, String sourceAlias) {
        DSConnection connection = new DSConnection();
        connection.setId(connectionId);
        connection.setDbType("POSTGRESQL");
        connection.setStatus("ENABLED");

        DSCatalog catalog = new DSCatalog();
        catalog.setId(1L);
        catalog.setCatalogValue("public");

        DSSource source = new DSSource();
        source.setId(connectionId);
        source.setConnectionId(connectionId);
        source.setSourceAlias(sourceAlias);
        if ("customer_order_ext".equals(sourceAlias)) {
            source.setConfigJson("""
                {
                  "joinType": "LEFT",
                  "lookupParam": "customerIds",
                  "lookupSourceColumn": "customer_id",
                  "parentJoinField": "customerId",
                  "childJoinField": "orderCustomerId",
                  "childSqlTemplate": "select customer_id as customer_order_ext_customer_id, order_amount as customer_order_ext_order_amount from customer_order_ext where customer_id in (/* customerIds */(0))"
                }
                """);
        }
        return new DataServiceRuntimeSource(source, connection, catalog);
    }


    private DSParam param(String name, String type, String placeholder) {
        DSParam param = new DSParam();
        param.setParamName(name);
        param.setParamType(type);
        param.setSqlPlaceholder(placeholder);
        param.setRequired(true);
        return param;
    }

    private DSField field(String sourceAlias,
                          String sourceColumn,
                          String fieldName,
                          String fieldType,
                          int sortOrder,
                          boolean joinKey) {
        DSField field = new DSField();
        field.setSourceAlias(sourceAlias);
        field.setSourceColumn(sourceColumn);
        field.setFieldName(fieldName);
        field.setFieldType(fieldType);
        field.setSortOrder(sortOrder);
        field.setJoinKey(joinKey);
        return field;
    }
}
