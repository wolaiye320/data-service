package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SingleSourceQueryExecutor {

    private final PreviewQueryService previewQueryService;
    private final FieldSnapshotViewService fieldSnapshotViewService;
    private final SourceSnapshotViewService sourceSnapshotViewService;

    public SingleSourceQueryExecutor(PreviewQueryService previewQueryService,
                                     FieldSnapshotViewService fieldSnapshotViewService,
                                     SourceSnapshotViewService sourceSnapshotViewService) {
        this.previewQueryService = previewQueryService;
        this.fieldSnapshotViewService = fieldSnapshotViewService;
        this.sourceSnapshotViewService = sourceSnapshotViewService;
    }

    /**
     * 执行单源正式查询，并基于已发布字段快照输出稳定结果顺序和诊断摘要。
     */
    public SingleSourceQueryResult execute(DsServiceRecord service,
                                           DsServiceVersionRecord version,
                                           Map<String, Object> boundParams) {
        if (!"SIMPLE_SQL".equals(version.getSqlType())) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "当前正式查询暂仅支持 SIMPLE_SQL 单源执行");
        }
        List<SqlFieldSnapshotService.FieldSnapshot> fields =
                fieldSnapshotViewService.toItems(version.getFieldSnapshotJson());
        PreviewQueryService.PreviewQueryResult previewResult = previewQueryService.executeBound(service, version, boundParams);
        List<Map<String, Object>> rows = previewResult.rows();
        List<Map<String, Object>> mappedRows = mapRows(rows, fields);
        List<String> orderedFieldNames = fields.stream()
                .map(SqlFieldSnapshotService.FieldSnapshot::fieldName)
                .toList();
        Map<String, Object> diagnosticSummary = new LinkedHashMap<>(previewResult.diagnosticSummary());
        diagnosticSummary.put("rowCount", mappedRows.size());
        diagnosticSummary.put("fieldCount", orderedFieldNames.size());
        diagnosticSummary.put("orderedFields", orderedFieldNames);
        diagnosticSummary.put("sourceCount", sourceSnapshotViewService.toItems(version.getSourceSnapshotJson()).size());
        diagnosticSummary.put("boundParamCount", boundParams == null ? 0 : boundParams.size());
        diagnosticSummary.put("maxResultRows", service.getMaxResultRows());
        diagnosticSummary.put("queryTimeoutSeconds", service.getQueryTimeoutSeconds());
        return new SingleSourceQueryResult(mappedRows, previewResult.elapsedMs(), diagnosticSummary);
    }

    private List<Map<String, Object>> mapRows(List<Map<String, Object>> rows,
                                              List<SqlFieldSnapshotService.FieldSnapshot> fields) {
        if (fields == null || fields.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .map(row -> mapRow(row, fields))
                .toList();
    }

    private Map<String, Object> mapRow(Map<String, Object> row, List<SqlFieldSnapshotService.FieldSnapshot> fields) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (SqlFieldSnapshotService.FieldSnapshot field : fields) {
            mapped.put(field.fieldName(), findValue(row, field.fieldName()));
        }
        return mapped;
    }

    private Object findValue(Map<String, Object> row, String fieldName) {
        if (row.containsKey(fieldName)) {
            return row.get(fieldName);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(fieldName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public record SingleSourceQueryResult(
            List<Map<String, Object>> rows,
            long elapsedMs,
            Map<String, Object> diagnosticSummary
    ) {
    }
}
