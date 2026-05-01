package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.FederatedQueryExecutionException;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行联邦正式查询的最小闭环，负责远端任务调度、本地 Join、残余过滤和字段投影。
 */
@Service
public class FederatedQueryExecutor {

    private static final Pattern SIMPLE_FILTER_PATTERN = Pattern.compile(
            "(?i)([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\s*(=|<>|>=|<=|>|<)\\s*(.+)"
    );
    private static final Pattern PARAM_PATTERN = Pattern.compile("/\\*\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\*/");

    private final PreviewQueryService previewQueryService;
    private final FieldSnapshotViewService fieldSnapshotViewService;
    private final FederatedQuerySplitter federatedQuerySplitter;
    private final LogicalPlanService logicalPlanService;
    private final FederatedExecutionGuardService federatedExecutionGuardService;
    private final FederatedLocalMemoryGuardService federatedLocalMemoryGuardService;

    public FederatedQueryExecutor(PreviewQueryService previewQueryService,
                                  FieldSnapshotViewService fieldSnapshotViewService,
                                  FederatedQuerySplitter federatedQuerySplitter,
                                  LogicalPlanService logicalPlanService,
                                  FederatedExecutionGuardService federatedExecutionGuardService,
                                  FederatedLocalMemoryGuardService federatedLocalMemoryGuardService) {
        this.previewQueryService = previewQueryService;
        this.fieldSnapshotViewService = fieldSnapshotViewService;
        this.federatedQuerySplitter = federatedQuerySplitter;
        this.logicalPlanService = logicalPlanService;
        this.federatedExecutionGuardService = federatedExecutionGuardService;
        this.federatedLocalMemoryGuardService = federatedLocalMemoryGuardService;
    }

    /**
     * 执行联邦正式查询的最小闭环：远端任务拆分、顺序调度、本地等值 Join、残余过滤与字段映射。
     */
    public FederatedQueryResult execute(DsServiceRecord service,
                                        DsServiceVersionRecord version,
                                        Map<String, Object> boundParams) {
        // 逻辑计划和拆分计划都来自发布快照，保证正式查询与发布时确认过的语义保持一致。
        LogicalPlanService.LogicalPlanSummary logicalPlanSummary = logicalPlanService.build(
                version.getSqlType(),
                version.getSqlText(),
                version.getSourceSnapshotJson(),
                version.getFieldSnapshotJson(),
                boundParams == null ? 0 : boundParams.size(),
                List.of()
        );
        FederatedQuerySplitter.FederatedSplitPlan splitPlan = federatedQuerySplitter.split(
                version.getSqlText(),
                version.getSourceSnapshotJson(),
                version.getParamSnapshotJson(),
                logicalPlanSummary
        );
        if (splitPlan.tasks().isEmpty()) {
            throw new FederatedQueryExecutionException(
                    "联邦执行拆分失败",
                    List.of(new FederatedQueryExecutionException.FederatedDiagnostic(
                            "execution.split",
                            "EMPTY_REMOTE_TASKS",
                            null,
                            null,
                            "联邦执行未生成任何远端查询任务"
                    ))
            );
        }
        try (FederatedExecutionGuardService.GuardPermit ignored = federatedExecutionGuardService.acquire()) {
            long startedAt = System.currentTimeMillis();
            Map<String, List<Map<String, Object>>> remoteRows = new LinkedHashMap<>();
            List<Map<String, Object>> taskSummaries = new ArrayList<>();

            // 远端任务逐个调度，先把数据按来源别名打平到本地命名空间，再进入本地整合阶段。
            for (FederatedQuerySplitter.FederatedRemoteTask task : splitPlan.tasks()) {
                List<Map<String, Object>> rows = executeTask(service, version, task, boundParams);
                remoteRows.put(task.alias().toLowerCase(Locale.ROOT), rows);
                taskSummaries.add(Map.of(
                        "taskId", task.taskId(),
                        "connectionCode", task.connectionCode(),
                        "alias", task.alias(),
                        "rowCount", rows.size()
                ));
            }

            // 本地 Join 和残余过滤都要经过内存保护，防止联邦补算在应用侧失控膨胀。
            List<Map<String, Object>> mergedRows = joinRows(splitPlan, remoteRows);
            federatedLocalMemoryGuardService.validate("FEDERATED_LOCAL_JOIN", mergedRows);
            List<Map<String, Object>> filteredRows = applyResidualFilters(mergedRows, splitPlan.residualFilters(), boundParams);
            federatedLocalMemoryGuardService.validate("FEDERATED_LOCAL_FILTER", filteredRows);
            List<Map<String, Object>> projectedRows = projectRows(filteredRows, version.getFieldSnapshotJson());
            long elapsedMs = System.currentTimeMillis() - startedAt;
            federatedExecutionGuardService.validateElapsed(service, elapsedMs);
            Map<String, Object> diagnosticSummary = new LinkedHashMap<>();
            diagnosticSummary.put("stage", "FEDERATED_EXECUTION");
            diagnosticSummary.put("taskCount", splitPlan.tasks().size());
            diagnosticSummary.put("joinConditionCount", splitPlan.joinConditions().size());
            diagnosticSummary.put("residualFilterCount", splitPlan.residualFilters().size());
            diagnosticSummary.put("integratedRowCount", projectedRows.size());
            diagnosticSummary.put("taskSummaries", taskSummaries);
            diagnosticSummary.put("rootNodeId", logicalPlanSummary.rootNodeId());
            diagnosticSummary.put("federatedQueryTimeoutSeconds", service.getFederatedQueryTimeoutSeconds());
            return new FederatedQueryResult(projectedRows, elapsedMs, diagnosticSummary);
        }
    }

    private List<Map<String, Object>> executeTask(DsServiceRecord service,
                                                  DsServiceVersionRecord version,
                                                  FederatedQuerySplitter.FederatedRemoteTask task,
                                                  Map<String, Object> boundParams) {
        DsServiceRecord taskService = new DsServiceRecord();
        taskService.setDefaultConnectionCode(task.connectionCode());
        taskService.setMaxResultRows(service.getMaxResultRows());
        taskService.setQueryTimeoutSeconds(service.getFederatedQueryTimeoutSeconds() == null
                ? service.getQueryTimeoutSeconds()
                : service.getFederatedQueryTimeoutSeconds());

        DsServiceVersionRecord taskVersion = new DsServiceVersionRecord();
        taskVersion.setSqlType("SIMPLE_SQL");
        taskVersion.setSqlText(task.sql());
        taskVersion.setParamSnapshotJson(task.paramSnapshotJson());
        taskVersion.setFieldSnapshotJson(version.getFieldSnapshotJson());

        PreviewQueryService.PreviewQueryResult result =
                previewQueryService.executeBound(taskService, taskVersion, boundParams, "FEDERATED_EXECUTION");
        if (Boolean.TRUE.equals(result.diagnosticSummary().get("truncated"))) {
            // 联邦远端结果一旦被截断，本地继续 Join 会直接破坏结果完整性，因此必须硬失败。
            throw cn.dtkeys.dataservice.core.error.ResourceProtectionException.resultRowsExceeded(
                    "FEDERATED_EXECUTION",
                    service.getMaxResultRows(),
                    service.getMaxResultRows() + 1
            );
        }
        return result.rows().stream()
                .map(row -> namespaceRow(row, task.alias()))
                .toList();
    }

    private Map<String, Object> namespaceRow(Map<String, Object> row, String alias) {
        Map<String, Object> namespaced = new LinkedHashMap<>();
        String normalizedAlias = alias == null ? "" : alias.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String normalizedField = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            namespaced.put(normalizedAlias + "." + normalizedField, entry.getValue());
        }
        return namespaced;
    }

    private List<Map<String, Object>> joinRows(FederatedQuerySplitter.FederatedSplitPlan splitPlan,
                                               Map<String, List<Map<String, Object>>> remoteRows) {
        List<FederatedQuerySplitter.FederatedRemoteTask> tasks = splitPlan.tasks();
        String firstAlias = tasks.getFirst().alias().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> merged = new ArrayList<>(remoteRows.getOrDefault(firstAlias, List.of()));
        for (int index = 1; index < tasks.size(); index++) {
            String alias = tasks.get(index).alias().toLowerCase(Locale.ROOT);
            List<Map<String, Object>> nextRows = remoteRows.getOrDefault(alias, List.of());

            // 这里只接受显式 Join 条件；缺条件时不能偷偷退化成笛卡尔积或模糊匹配。
            List<FederatedQuerySplitter.JoinCondition> relevantConditions = splitPlan.joinConditions().stream()
                    .filter(condition -> condition.leftAlias().equalsIgnoreCase(alias)
                            || condition.rightAlias().equalsIgnoreCase(alias))
                    .toList();
            if (relevantConditions.isEmpty()) {
                throw new FederatedQueryExecutionException(
                        "联邦本地整合失败",
                        List.of(new FederatedQueryExecutionException.FederatedDiagnostic(
                                "execution.join",
                                "MISSING_JOIN_CONDITION",
                                tasks.get(index).connectionCode(),
                                tasks.get(index).alias(),
                                "远端任务缺少可用的 Join 条件"
                        ))
                );
            }
            List<Map<String, Object>> joined = new ArrayList<>();
            for (Map<String, Object> leftRow : merged) {
                for (Map<String, Object> rightRow : nextRows) {
                    if (matchesAllConditions(leftRow, rightRow, relevantConditions, alias)) {
                        Map<String, Object> combined = new LinkedHashMap<>(leftRow);
                        combined.putAll(rightRow);
                        joined.add(combined);
                    }
                }
            }
            merged = joined;
        }
        return merged;
    }

    private boolean matchesAllConditions(Map<String, Object> mergedRow,
                                         Map<String, Object> nextRow,
                                         List<FederatedQuerySplitter.JoinCondition> conditions,
                                         String nextAlias) {
        for (FederatedQuerySplitter.JoinCondition condition : conditions) {
            Object leftValue = readJoinValue(mergedRow, nextRow, condition.leftAlias(), condition.leftField(), nextAlias);
            Object rightValue = readJoinValue(mergedRow, nextRow, condition.rightAlias(), condition.rightField(), nextAlias);
            if (leftValue == null || rightValue == null || !leftValue.equals(rightValue)) {
                return false;
            }
        }
        return true;
    }

    private Object readJoinValue(Map<String, Object> mergedRow,
                                 Map<String, Object> nextRow,
                                 String alias,
                                 String field,
                                 String nextAlias) {
        String key = alias.toLowerCase(Locale.ROOT) + "." + field.toLowerCase(Locale.ROOT);
        return alias.equalsIgnoreCase(nextAlias) ? nextRow.get(key) : mergedRow.get(key);
    }

    private List<Map<String, Object>> applyResidualFilters(List<Map<String, Object>> rows,
                                                           List<String> residualFilters,
                                                           Map<String, Object> boundParams) {
        if (residualFilters == null || residualFilters.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> filtered = new ArrayList<>(rows);
        for (String expression : residualFilters) {
            filtered = filtered.stream()
                    .filter(row -> evaluateFilter(row, expression, boundParams))
                    .toList();
        }
        return filtered;
    }

    private boolean evaluateFilter(Map<String, Object> row, String expression, Map<String, Object> boundParams) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        if (expression.toLowerCase(Locale.ROOT).contains(" or ")) {
            // 当前最小闭环只实现了 AND 下的简单表达式，超出能力边界必须明确失败。
            throw new FederatedQueryExecutionException(
                    "联邦残余过滤执行失败",
                    List.of(new FederatedQueryExecutionException.FederatedDiagnostic(
                            "execution.localFilter",
                            "UNSUPPORTED_RESIDUAL_FILTER",
                            null,
                            null,
                            "当前最小闭环仅支持 AND 结构下的简单残余过滤"
                    ))
            );
        }
        Matcher matcher = SIMPLE_FILTER_PATTERN.matcher(expression.trim());
        if (!matcher.matches()) {
            throw new FederatedQueryExecutionException(
                    "联邦残余过滤执行失败",
                    List.of(new FederatedQueryExecutionException.FederatedDiagnostic(
                            "execution.localFilter",
                            "UNSUPPORTED_RESIDUAL_FILTER",
                            null,
                            null,
                            "当前最小闭环不支持的残余过滤表达式: " + expression
                    ))
            );
        }
        Object leftValue = row.get(matcher.group(1).toLowerCase(Locale.ROOT) + "." + matcher.group(2).toLowerCase(Locale.ROOT));
        Object rightValue = resolveFilterValue(row, matcher.group(4), boundParams);
        return compare(leftValue, rightValue, matcher.group(3));
    }

    private Object resolveFilterValue(Map<String, Object> row, String token, Map<String, Object> boundParams) {
        String trimmed = token.trim();
        Matcher paramMatcher = PARAM_PATTERN.matcher(trimmed);
        if (paramMatcher.find()) {
            return boundParams.get(paramMatcher.group(1));
        }
        if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.matches("[a-zA-Z_][a-zA-Z0-9_]*\\.[a-zA-Z_][a-zA-Z0-9_]*")) {
            return row.get(trimmed.toLowerCase(Locale.ROOT));
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException ex) {
            return trimmed;
        }
    }

    private boolean compare(Object leftValue, Object rightValue, String operator) {
        if ("=".equals(operator)) {
            return normalizeValue(leftValue).equals(normalizeValue(rightValue));
        }
        if ("<>".equals(operator)) {
            return !normalizeValue(leftValue).equals(normalizeValue(rightValue));
        }
        if (leftValue == null || rightValue == null) {
            return false;
        }
        BigDecimal leftDecimal = asDecimal(leftValue);
        BigDecimal rightDecimal = asDecimal(rightValue);
        int result;
        if (leftDecimal != null && rightDecimal != null) {
            result = leftDecimal.compareTo(rightDecimal);
        } else {
            result = String.valueOf(leftValue).compareTo(String.valueOf(rightValue));
        }
        return switch (operator) {
            case ">" -> result > 0;
            case "<" -> result < 0;
            case ">=" -> result >= 0;
            case "<=" -> result <= 0;
            default -> false;
        };
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        return value;
    }

    private BigDecimal asDecimal(Object value) {
        Object normalized = normalizeValue(value);
        return normalized instanceof BigDecimal decimal ? decimal : null;
    }

    private List<Map<String, Object>> projectRows(List<Map<String, Object>> rows, String fieldSnapshotJson) {
        List<SqlFieldSnapshotService.FieldSnapshot> fields = fieldSnapshotViewService.toItems(fieldSnapshotJson).stream()
                .sorted(Comparator.comparingInt(SqlFieldSnapshotService.FieldSnapshot::sortOrder))
                .toList();
        if (fields.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .map(row -> projectRow(row, fields))
                .toList();
    }

    private Map<String, Object> projectRow(Map<String, Object> row, List<SqlFieldSnapshotService.FieldSnapshot> fields) {
        Map<String, Object> projected = new LinkedHashMap<>();
        for (SqlFieldSnapshotService.FieldSnapshot field : fields) {
            projected.put(field.fieldName(), resolveProjectionValue(row, field));
        }
        return projected;
    }

    private Object resolveProjectionValue(Map<String, Object> row, SqlFieldSnapshotService.FieldSnapshot field) {
        String expression = field.expression();
        if (expression != null && expression.matches("[a-zA-Z_][a-zA-Z0-9_]*\\.[a-zA-Z_][a-zA-Z0-9_]*")) {
            return row.get(expression.toLowerCase(Locale.ROOT));
        }
        if (expression != null && expression.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            List<Object> candidates = row.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith("." + expression.toLowerCase(Locale.ROOT)))
                    .map(Map.Entry::getValue)
                    .distinct()
                    .toList();
            if (candidates.size() == 1) {
                return candidates.getFirst();
            }
        }
        throw new FederatedQueryExecutionException(
                "联邦字段映射失败",
                List.of(new FederatedQueryExecutionException.FederatedDiagnostic(
                        "execution.projection",
                        "UNSUPPORTED_FIELD_EXPRESSION",
                        null,
                        null,
                        "当前最小闭环不支持的联邦字段表达式: " + expression
                ))
        );
    }

    public record FederatedQueryResult(
            List<Map<String, Object>> rows,
            long elapsedMs,
            Map<String, Object> diagnosticSummary
    ) {
    }
}
