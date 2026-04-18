package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.federation.dialect.DialectAdapterRegistry;
import cn.dtkeys.dataservice.federation.model.ExecutionStageResult;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RemoteQueryExecutor {

    private final DialectAdapterRegistry dialectAdapterRegistry = new DialectAdapterRegistry();

    public ExecutionStageResult execute(FederatedPlanStage stage, Map<String, ExecutionStageResult> completedStages) {
        String executedSql = dialectAdapterRegistry.adaptSql(stage.sourceType(), applyDynamicFilter(stage, completedStages));
        List<Map<String, Object>> rows = buildRows(stage, executedSql);

        Map<String, Object> executionSummary = Map.of(
            "remoteExecutable", stage.remoteExecutable(),
            "dependsOnStageIds", stage.dependsOnStageIds(),
            "dynamicFilterEnabled", stage.dynamicFilterEnabled(),
            "concurrencyGroup", stage.concurrencyGroup(),
            "attributes", stage.attributes(),
            "rowCount", rows.size(),
            "threadName", Thread.currentThread().getName()
        );
        return new ExecutionStageResult(stage.stageId(), stage.source(), executedSql, rows, executionSummary);
    }

    private List<Map<String, Object>> buildRows(FederatedPlanStage stage, String executedSql) {
        int rowCount = resolveSimulatedRowCount(stage);
        List<Map<String, Object>> rows = new java.util.ArrayList<>(rowCount);
        for (int index = 0; index < rowCount; index++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stageId", stage.stageId());
            row.put("source", stage.source());
            row.put("sourceType", stage.sourceType());
            row.put("sql", executedSql);
            row.put("projectedFields", stage.projectedFields());
            row.put("filter", stage.pushedFilter());
            row.put("dynamicFilterApplied", stage.dynamicFilterEnabled() && !stage.dependsOnStageIds().isEmpty());
            row.put("rowIndex", index);
            if (stage.dynamicFilterField() != null && !stage.dynamicFilterField().isBlank()) {
                row.put(stage.dynamicFilterField(), stage.source() + "-" + stage.stageId() + "-" + index);
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private int resolveSimulatedRowCount(FederatedPlanStage stage) {
        Object configured = stage.attributes().get("simulatedRowCount");
        if (configured instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return 1;
    }

    private String applyDynamicFilter(FederatedPlanStage stage, Map<String, ExecutionStageResult> completedStages) {
        if (!stage.dynamicFilterEnabled() || stage.dynamicFilterField() == null || stage.dynamicFilterField().isBlank()) {
            return stage.sql();
        }
        List<Object> filterValues = stage.dependsOnStageIds().stream()
            .map(completedStages::get)
            .filter(result -> result != null)
            .flatMap(result -> result.rows().stream())
            .map(row -> row.get(stage.dynamicFilterField()))
            .filter(value -> value != null)
            .distinct()
            .toList();
        if (filterValues.isEmpty()) {
            return stage.sql();
        }
        String inClause = filterValues.stream()
            .map(value -> "'" + String.valueOf(value) + "'")
            .collect(Collectors.joining(", "));
        if (stage.sql().toUpperCase().contains(" WHERE ")) {
            return stage.sql() + " AND " + stage.dynamicFilterField() + " IN (" + inClause + ")";
        }
        return stage.sql() + " WHERE " + stage.dynamicFilterField() + " IN (" + inClause + ")";
    }
}
