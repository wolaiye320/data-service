package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SingleSourceQueryExecutorTest {

    private final PreviewQueryService previewQueryService = Mockito.mock(PreviewQueryService.class);
    private final SingleSourceQueryExecutor executor = new SingleSourceQueryExecutor(
            previewQueryService,
            new FieldSnapshotViewService(new ObjectMapper()),
            new SourceSnapshotViewService(new ObjectMapper())
    );

    @Test
    void shouldMapRowsByFieldSnapshotOrderAndBuildDiagnosticSummary() {
        DsServiceRecord service = new DsServiceRecord();
        service.setMaxResultRows(100);
        service.setQueryTimeoutSeconds(30);

        DsServiceVersionRecord version = new DsServiceVersionRecord();
        version.setSqlType("SIMPLE_SQL");
        version.setFieldSnapshotJson("""
                [
                  {"fieldName":"order_name","expression":"order_name","sortOrder":2},
                  {"fieldName":"id","expression":"id","sortOrder":1}
                ]
                """);
        version.setSourceSnapshotJson("""
                [
                  {"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"}
                ]
                """);

        Map<String, Object> rawRow = new LinkedHashMap<>();
        rawRow.put("ID", 1L);
        rawRow.put("ORDER_NAME", "order-a");
        when(previewQueryService.executeBound(eq(service), eq(version), any(Map.class)))
                .thenReturn(new PreviewQueryService.PreviewQueryResult(
                        List.of(rawRow),
                        12L,
                        Map.of("connectionCode", "PG_MAIN", "sqlType", "SIMPLE_SQL")
                ));

        SingleSourceQueryExecutor.SingleSourceQueryResult result =
                executor.execute(service, version, Map.of("orderId", 1L));

        assertEquals(List.of("order_name", "id"), result.diagnosticSummary().get("orderedFields"));
        assertEquals(1, result.diagnosticSummary().get("sourceCount"));
        assertEquals(1, result.diagnosticSummary().get("boundParamCount"));
        assertEquals(12L, result.elapsedMs());
        assertEquals("order-a", result.rows().getFirst().get("order_name"));
        assertEquals(1L, result.rows().getFirst().get("id"));
    }
}
