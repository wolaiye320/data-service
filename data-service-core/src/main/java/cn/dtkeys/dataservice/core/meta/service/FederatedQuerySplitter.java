package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FederatedQuerySplitter {

    private static final TypeReference<List<DomaSqlTemplateParser.ParamSnapshot>> PARAM_SNAPSHOT_LIST =
            new TypeReference<>() {
            };
    private static final Pattern JOIN_PATTERN = Pattern.compile(
            "(?i)([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)"
    );

    private final ObjectMapper objectMapper;
    private final SourceSnapshotViewService sourceSnapshotViewService;

    public FederatedQuerySplitter(ObjectMapper objectMapper,
                                  SourceSnapshotViewService sourceSnapshotViewService) {
        this.objectMapper = objectMapper;
        this.sourceSnapshotViewService = sourceSnapshotViewService;
    }

    /**
     * 基于逻辑计划摘要与来源快照拆分联邦远端查询任务。
     */
    public FederatedSplitPlan split(String sqlText,
                                    String sourceSnapshotJson,
                                    String paramSnapshotJson,
                                    LogicalPlanService.LogicalPlanSummary logicalPlanSummary) {
        List<SourceSnapshotItemResponse> sources = sourceSnapshotViewService.toItems(sourceSnapshotJson);
        List<SourceSnapshotItemResponse> orderedSources = logicalPlanSummary != null
                && logicalPlanSummary.joinReorder() != null
                && logicalPlanSummary.joinReorder().applied()
                ? logicalPlanSummary.joinReorder().reorderedSources()
                : sources;
        Map<String, SourceSnapshotItemResponse> sourceByAlias = new LinkedHashMap<>();
        for (SourceSnapshotItemResponse source : sources) {
            if (source.alias() != null && !source.alias().isBlank()) {
                sourceByAlias.put(source.alias().toLowerCase(Locale.ROOT), source);
            }
        }
        List<FilterPushdownService.FilterCondition> pushdownConditions = logicalPlanSummary == null
                || logicalPlanSummary.filterPushdown() == null
                ? List.of()
                : logicalPlanSummary.filterPushdown().pushdownConditions();
        List<FilterPushdownService.FilterCondition> residualConditions = logicalPlanSummary == null
                || logicalPlanSummary.filterPushdown() == null
                ? List.of()
                : logicalPlanSummary.filterPushdown().residualConditions();
        List<DomaSqlTemplateParser.ParamSnapshot> snapshots = parseSnapshots(paramSnapshotJson);
        List<FederatedRemoteTask> tasks = new ArrayList<>();
        for (int index = 0; index < orderedSources.size(); index++) {
            SourceSnapshotItemResponse source = orderedSources.get(index);
            List<FilterPushdownService.FilterCondition> taskConditions = pushdownConditions.stream()
                    .filter(condition -> matchesSource(condition, source))
                    .toList();
            String remoteSql = buildRemoteSql(source, taskConditions);
            tasks.add(new FederatedRemoteTask(
                    "FED_TASK_" + (index + 1),
                    source.connectionCode(),
                    source.alias(),
                    source.dbType(),
                    remoteSql,
                    toJson(filterSnapshotsForSql(snapshots, remoteSql)),
                    taskConditions.stream().map(FilterPushdownService.FilterCondition::expression).toList()
            ));
        }
        return new FederatedSplitPlan(
                tasks,
                parseJoinConditions(sqlText, sourceByAlias),
                residualConditions.stream().map(FilterPushdownService.FilterCondition::expression).toList()
        );
    }

    private boolean matchesSource(FilterPushdownService.FilterCondition condition, SourceSnapshotItemResponse source) {
        if (condition.connectionCode() != null && source.connectionCode() != null) {
            return source.connectionCode().equalsIgnoreCase(condition.connectionCode());
        }
        return condition.alias() != null
                && source.alias() != null
                && source.alias().equalsIgnoreCase(condition.alias());
    }

    private String buildRemoteSql(SourceSnapshotItemResponse source,
                                  List<FilterPushdownService.FilterCondition> taskConditions) {
        String qualifiedTable = source.schemaName() == null || source.schemaName().isBlank()
                ? source.databaseName() + "." + source.tableName()
                : source.schemaName() + "." + source.tableName();
        String aliasSql = source.alias() == null || source.alias().isBlank() ? "" : " " + source.alias();
        if (taskConditions.isEmpty()) {
            return "select * from " + qualifiedTable + aliasSql;
        }
        String whereClause = String.join(" and ", taskConditions.stream()
                .map(FilterPushdownService.FilterCondition::expression)
                .toList());
        return "select * from " + qualifiedTable + aliasSql + " where " + whereClause;
    }

    private List<JoinCondition> parseJoinConditions(String sqlText, Map<String, SourceSnapshotItemResponse> sourceByAlias) {
        if (sqlText == null || sqlText.isBlank()) {
            return List.of();
        }
        List<JoinCondition> conditions = new ArrayList<>();
        Matcher matcher = JOIN_PATTERN.matcher(sqlText);
        while (matcher.find()) {
            String leftAlias = matcher.group(1);
            String leftField = matcher.group(2);
            String rightAlias = matcher.group(3);
            String rightField = matcher.group(4);
            if (!sourceByAlias.containsKey(leftAlias.toLowerCase(Locale.ROOT))
                    || !sourceByAlias.containsKey(rightAlias.toLowerCase(Locale.ROOT))) {
                continue;
            }
            conditions.add(new JoinCondition(
                    matcher.group(),
                    leftAlias,
                    leftField,
                    rightAlias,
                    rightField
            ));
        }
        return conditions;
    }

    private List<DomaSqlTemplateParser.ParamSnapshot> filterSnapshotsForSql(List<DomaSqlTemplateParser.ParamSnapshot> snapshots,
                                                                            String sqlText) {
        return snapshots.stream()
                .filter(snapshot -> sqlText.contains(snapshot.placeholder()))
                .toList();
    }

    private List<DomaSqlTemplateParser.ParamSnapshot> parseSnapshots(String paramSnapshotJson) {
        if (paramSnapshotJson == null || paramSnapshotJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(paramSnapshotJson, PARAM_SNAPSHOT_LIST);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "联邦参数快照反序列化失败", ex);
        }
    }

    private String toJson(List<DomaSqlTemplateParser.ParamSnapshot> snapshots) {
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "联邦参数快照序列化失败", ex);
        }
    }

    public record FederatedRemoteTask(
            String taskId,
            String connectionCode,
            String alias,
            String dbType,
            String sql,
            String paramSnapshotJson,
            List<String> pushdownConditions
    ) {
    }

    public record JoinCondition(
            String expression,
            String leftAlias,
            String leftField,
            String rightAlias,
            String rightField
    ) {
    }

    public record FederatedSplitPlan(
            List<FederatedRemoteTask> tasks,
            List<JoinCondition> joinConditions,
            List<String> residualFilters
    ) {
    }
}
