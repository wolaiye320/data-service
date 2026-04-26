package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 基于来源、字段和参数快照生成逻辑计划节点。
 */
@Service
public class LogicalPlanService {

    private static final TypeReference<List<SqlFieldSnapshotService.FieldSnapshot>> FIELD_SNAPSHOT_LIST =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final SourceSnapshotViewService sourceSnapshotViewService;
    private final FilterPushdownService filterPushdownService;
    private final ProjectionPushdownService projectionPushdownService;
    private final LocalCompensationGuardService localCompensationGuardService;

    public LogicalPlanService(ObjectMapper objectMapper,
                              SourceSnapshotViewService sourceSnapshotViewService,
                              FilterPushdownService filterPushdownService,
                              ProjectionPushdownService projectionPushdownService,
                              LocalCompensationGuardService localCompensationGuardService) {
        this.objectMapper = objectMapper;
        this.sourceSnapshotViewService = sourceSnapshotViewService;
        this.filterPushdownService = filterPushdownService;
        this.projectionPushdownService = projectionPushdownService;
        this.localCompensationGuardService = localCompensationGuardService;
    }

    /**
     * 生成最小逻辑计划。当前覆盖来源扫描、过滤、投影、Join、聚合等主干节点。
     */
    public LogicalPlanSummary build(String sqlType,
                                    String sqlText,
                                    String sourceSnapshotJson,
                                    String fieldSnapshotJson,
                                    int paramCount,
                                    List<SourceCapabilityService.CapabilitySummary> capabilitySummaries) {
        List<LogicalPlanNode> nodes = new ArrayList<>();
        List<SourceSnapshotItemResponse> sources = sourceSnapshotViewService.toItems(sourceSnapshotJson);
        List<SqlFieldSnapshotService.FieldSnapshot> fields = parseFieldSnapshots(fieldSnapshotJson);
        JoinReorderSummary joinReorderSummary = buildJoinReorderSummary(sqlText, sources);
        FilterPushdownService.FilterPushdownSummary filterPushdownSummary =
                filterPushdownService.analyze(sqlText, sources, capabilitySummaries == null ? List.of() : capabilitySummaries);
        ProjectionPushdownService.ProjectionPushdownSummary projectionPushdownSummary =
                projectionPushdownService.analyze(fields, sources, capabilitySummaries == null ? List.of() : capabilitySummaries);
        LocalCompensationGuardService.LocalCompensationSummary localCompensationSummary =
                localCompensationGuardService.analyze(
                        sqlText,
                        sources.size(),
                        null,
                        filterPushdownSummary,
                        projectionPushdownSummary
                );

        List<SourceSnapshotItemResponse> orderedSources = joinReorderSummary.applied()
                ? joinReorderSummary.reorderedSources()
                : sources;

        for (int i = 0; i < orderedSources.size(); i++) {
            SourceSnapshotItemResponse source = orderedSources.get(i);
            nodes.add(new LogicalPlanNode(
                    "SCAN_" + (i + 1),
                    LogicalPlanNodeType.SCAN,
                    source.connectionCode() + "@" + source.schemaName() + "." + source.tableName(),
                    List.of()
            ));
        }
        if (containsJoin(sqlText) && sources.size() >= 2) {
            nodes.add(new LogicalPlanNode("JOIN_1", LogicalPlanNodeType.JOIN, "Join multiple sources", sourceNodeIds(nodes, LogicalPlanNodeType.SCAN)));
        }
        if (containsWhere(sqlText)) {
            String detail = filterPushdownSummary.pushdownConditions().isEmpty()
                    ? "Apply residual where predicate"
                    : "Apply where predicate with pushdown=" + filterPushdownSummary.pushdownConditions().size();
            nodes.add(new LogicalPlanNode("FILTER_1", LogicalPlanNodeType.FILTER, detail, previousNodeIds(nodes)));
        }
        if (containsAggregate(sqlText)) {
            nodes.add(new LogicalPlanNode("AGGREGATE_1", LogicalPlanNodeType.AGGREGATE, "Aggregate result set", previousNodeIds(nodes)));
        }
        if (!fields.isEmpty()) {
            nodes.add(new LogicalPlanNode(
                    "PROJECT_1",
                    LogicalPlanNodeType.PROJECT,
                    buildProjectDetail(projectionPushdownSummary),
                    previousNodeIds(nodes)
            ));
        }
        if (paramCount > 0) {
            nodes.add(new LogicalPlanNode("PARAM_BIND_1", LogicalPlanNodeType.PARAM_BIND, "Bind " + paramCount + " params", List.of("PROJECT_1")));
        }

        String rootNodeId = nodes.isEmpty() ? null : nodes.getLast().nodeId();
        return new LogicalPlanSummary(
                sqlType,
                sources.size(),
                fields.size(),
                paramCount,
                rootNodeId,
                nodes,
                joinReorderSummary,
                filterPushdownSummary,
                projectionPushdownSummary,
                localCompensationSummary
        );
    }

    public String toJson(LogicalPlanSummary summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "逻辑计划摘要序列化失败", ex);
        }
    }

    private List<SqlFieldSnapshotService.FieldSnapshot> parseFieldSnapshots(String fieldSnapshotJson) {
        if (fieldSnapshotJson == null || fieldSnapshotJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(fieldSnapshotJson, FIELD_SNAPSHOT_LIST);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "字段快照反序列化失败", ex);
        }
    }

    private boolean containsJoin(String sqlText) {
        return sqlText != null && sqlText.toLowerCase(Locale.ROOT).contains(" join ");
    }

    private boolean containsWhere(String sqlText) {
        return sqlText != null && sqlText.toLowerCase(Locale.ROOT).contains(" where ");
    }

    private boolean containsAggregate(String sqlText) {
        String lower = sqlText == null ? "" : sqlText.toLowerCase(Locale.ROOT);
        return lower.contains("count(") || lower.contains("sum(") || lower.contains("avg(")
                || lower.contains("min(") || lower.contains("max(");
    }

    private List<String> sourceNodeIds(List<LogicalPlanNode> nodes, LogicalPlanNodeType type) {
        return nodes.stream().filter(node -> node.nodeType() == type).map(LogicalPlanNode::nodeId).toList();
    }

    private List<String> previousNodeIds(List<LogicalPlanNode> nodes) {
        if (nodes.isEmpty()) {
            return List.of();
        }
        return List.of(nodes.getLast().nodeId());
    }

    private String buildProjectDetail(ProjectionPushdownService.ProjectionPushdownSummary projectionPushdownSummary) {
        if (projectionPushdownSummary == null || projectionPushdownSummary.requestedFields().isEmpty()) {
            return "Project []";
        }
        return "Project " + projectionPushdownSummary.fieldMapping().outputFields()
                + ", pushdown=" + projectionPushdownSummary.pushdownFields().size()
                + ", residual=" + projectionPushdownSummary.residualFields().size();
    }

    private JoinReorderSummary buildJoinReorderSummary(String sqlText, List<SourceSnapshotItemResponse> sources) {
        if (!containsJoin(sqlText) || sources.size() < 2) {
            return new JoinReorderSummary(List.of(), sources, sources, false, "No multi-source join detected");
        }
        List<JoinReorderInput> inputs = sources.stream()
                .map(source -> new JoinReorderInput(
                        source.connectionCode(),
                        source.dbType(),
                        estimateFilterScore(sqlText, source.alias()),
                        estimateSourceSize(source.tableName()),
                        "INNER"
                ))
                .toList();
        List<SourceSnapshotItemResponse> reorderedSources = inputs.stream()
                .sorted(Comparator.comparingInt(JoinReorderInput::filterScore).reversed()
                        .thenComparingInt(JoinReorderInput::estimatedRows)
                        .thenComparing(JoinReorderInput::connectionCode))
                .map(input -> sources.stream()
                        .filter(source -> source.connectionCode().equals(input.connectionCode()))
                        .findFirst()
                        .orElseThrow())
                .toList();
        boolean applied = !sourceOrder(sources).equals(sourceOrder(reorderedSources));
        String reason = applied
                ? "Reordered joins by filter selectivity and estimated source size"
                : "Join order already satisfied filter selectivity and estimated size";
        return new JoinReorderSummary(inputs, sources, reorderedSources, applied, reason);
    }

    private int estimateFilterScore(String sqlText, String alias) {
        if (sqlText == null || alias == null || alias.isBlank()) {
            return 0;
        }
        String lower = sqlText.toLowerCase(Locale.ROOT);
        String aliasPrefix = alias.toLowerCase(Locale.ROOT) + ".";
        int whereIndex = lower.indexOf(" where ");
        if (whereIndex < 0) {
            return 0;
        }
        return lower.substring(whereIndex).contains(aliasPrefix) ? 10 : 0;
    }

    private int estimateSourceSize(String tableName) {
        String normalized = tableName == null ? "" : tableName.toLowerCase(Locale.ROOT);
        if (normalized.contains("item") || normalized.contains("detail")) {
            return 1000;
        }
        return 100;
    }

    private List<String> sourceOrder(List<SourceSnapshotItemResponse> sources) {
        return sources.stream().map(SourceSnapshotItemResponse::connectionCode).toList();
    }

    public enum LogicalPlanNodeType {
        SCAN,
        JOIN,
        FILTER,
        AGGREGATE,
        PROJECT,
        PARAM_BIND
    }

    public record LogicalPlanNode(
            String nodeId,
            LogicalPlanNodeType nodeType,
            String detail,
            List<String> inputNodeIds
    ) {
    }

    public record LogicalPlanSummary(
            String sqlType,
            int sourceCount,
            int fieldCount,
            int paramCount,
            String rootNodeId,
            List<LogicalPlanNode> nodes,
            JoinReorderSummary joinReorder,
            FilterPushdownService.FilterPushdownSummary filterPushdown,
            ProjectionPushdownService.ProjectionPushdownSummary projectionPushdown,
            LocalCompensationGuardService.LocalCompensationSummary localCompensation
        ) {
    }

    public record JoinReorderInput(
            String connectionCode,
            String dbType,
            int filterScore,
            int estimatedRows,
            String joinType
    ) {
    }

    public record JoinReorderSummary(
            List<JoinReorderInput> inputs,
            List<SourceSnapshotItemResponse> originalSources,
            List<SourceSnapshotItemResponse> reorderedSources,
            boolean applied,
            String reason
    ) {
    }
}
