package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import cn.dtkeys.dataservice.service.model.DSField;
import cn.dtkeys.dataservice.service.model.DSParam;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeDefinition;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeSource;
import cn.dtkeys.dataservice.query.executor.BoundQuery;
import cn.dtkeys.dataservice.query.executor.NamedParameterQueryExecutor;
import cn.dtkeys.dataservice.query.executor.QueryParameterBinder;
import cn.dtkeys.dataservice.query.executor.QueryResultMapper;
import cn.dtkeys.dataservice.query.executor.SqlTemplateRenderer;
import cn.dtkeys.dataservice.query.executor.StreamingQueryRowHandler;
import cn.dtkeys.dataservice.query.executor.SqlReadOnlyValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PredefinedJoinQueryExecutor {

    private static final int DEFAULT_FETCH_SIZE = 200;
    private static final int DEFAULT_PREVIEW_ROWS = 200;

    private final QueryParameterBinder queryParameterBinder;
    private final NamedParameterQueryExecutor namedParameterQueryExecutor;
    private final QueryResultMapper queryResultMapper;
    private final SqlReadOnlyValidator sqlReadOnlyValidator;
    private final PredefinedJoinConfigParser predefinedJoinConfigParser;
    private final PredefinedJoinResultAssembler predefinedJoinResultAssembler;

    public PredefinedJoinQueryExecutor(QueryParameterBinder queryParameterBinder,
                                       NamedParameterQueryExecutor namedParameterQueryExecutor,
                                       QueryResultMapper queryResultMapper,
                                       SqlReadOnlyValidator sqlReadOnlyValidator,
                                       PredefinedJoinConfigParser predefinedJoinConfigParser,
                                       PredefinedJoinResultAssembler predefinedJoinResultAssembler) {
        this.queryParameterBinder = queryParameterBinder;
        this.namedParameterQueryExecutor = namedParameterQueryExecutor;
        this.queryResultMapper = queryResultMapper;
        this.sqlReadOnlyValidator = sqlReadOnlyValidator;
        this.predefinedJoinConfigParser = predefinedJoinConfigParser;
        this.predefinedJoinResultAssembler = predefinedJoinResultAssembler;
    }

    public List<Map<String, Object>> execute(DataServiceRuntimeDefinition runtimeDefinition,
                                             Map<String, Object> params,
                                             int queryTimeoutSeconds,
                                             int maxResultRows) {
        return executeWithSummary(runtimeDefinition, params, queryTimeoutSeconds, maxResultRows).rows();
    }

    public PredefinedJoinExecutionResult executeWithSummary(DataServiceRuntimeDefinition runtimeDefinition,
                                                            Map<String, Object> params,
                                                            int queryTimeoutSeconds,
                                                            int maxResultRows) {
        if (runtimeDefinition.sources().size() < 2) {
            throw new ServiceConfigInvalidException("预定义跨库组合查询至少需要两个来源");
        }

        DataServiceRuntimeSource primarySource = runtimeDefinition.sources().get(0);
        DataServiceRuntimeSource firstChildSource = runtimeDefinition.sources().get(1);
        PredefinedJoinConfig firstJoinConfig = predefinedJoinConfigParser.parse(firstChildSource.source().getConfigJson());
        validateJoinFields(runtimeDefinition, primarySource, firstChildSource, firstJoinConfig);

        if (canPushDownToDatabase(primarySource, firstChildSource, params, firstJoinConfig)
            && runtimeDefinition.sources().size() == 2) {
            List<Map<String, Object>> rows = executePushedDownJoin(runtimeDefinition, primarySource, firstChildSource,
                firstJoinConfig, params, queryTimeoutSeconds, maxResultRows);
            return new PredefinedJoinExecutionResult(rows, buildPushdownSummary(rows.size(), primarySource, firstChildSource));
        }

        try (FederatedExecutionBuffer resultBuffer = new FederatedExecutionBuffer(
            Math.min(DEFAULT_PREVIEW_ROWS, maxResultRows),
            maxResultRows,
            Math.max(1, Math.min(DEFAULT_FETCH_SIZE, maxResultRows))
        )) {
            List<Map<String, Object>> stageSummaries = new ArrayList<>();
            List<Map<String, Object>> primaryRows = executePrimarySql(runtimeDefinition, primarySource, params, queryTimeoutSeconds,
                maxResultRows);
            stageSummaries.add(stageSummary("stage-1", primarySource.source().getSourceAlias(), primaryRows.size(), false));

            List<Map<String, Object>> currentRows = primaryRows;
            for (int i = 1; i < runtimeDefinition.sources().size(); i++) {
                DataServiceRuntimeSource childSource = runtimeDefinition.sources().get(i);
                PredefinedJoinConfig joinConfig = predefinedJoinConfigParser.parse(childSource.source().getConfigJson());
                validateJoinFields(runtimeDefinition, primarySource, childSource, joinConfig);
                List<Map<String, Object>> childRows = executeChildLookup(runtimeDefinition, childSource, currentRows, joinConfig,
                    queryTimeoutSeconds, maxResultRows);
                stageSummaries.add(stageSummary("stage-" + (i + 1), childSource.source().getSourceAlias(), childRows.size(), true));
                currentRows = predefinedJoinResultAssembler.assemble(currentRows, childRows, joinConfig);
            }
            resultBuffer.appendRows(currentRows);
            return new PredefinedJoinExecutionResult(
                resultBuffer.previewRows(),
                buildLookupSummary(resultBuffer, stageSummaries)
            );
        }
    }

    private Map<String, Object> buildPushdownSummary(int rowCount,
                                                     DataServiceRuntimeSource primarySource,
                                                     DataServiceRuntimeSource childSource) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", "PUSHDOWN");
        summary.put("spillTriggered", false);
        summary.put("rowCount", rowCount);
        summary.put("maxParallelism", 1);
        summary.put("stageWaveCount", 1);
        summary.put("dynamicFilterStageCount", 0);
        summary.put("executedStageCount", 1);
        summary.put("stageSummaries", List.of(
            stageSummary(
                "stage-1",
                primarySource.source().getSourceAlias() + "+" + childSource.source().getSourceAlias(),
                rowCount,
                false
            )
        ));
        return summary;
    }

    private Map<String, Object> buildLookupSummary(FederatedExecutionBuffer resultBuffer,
                                                   List<Map<String, Object>> stageSummaries) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", "LOOKUP");
        summary.put("spillTriggered", resultBuffer.summary().get("spillTriggered"));
        summary.put("maxParallelism", 1);
        summary.put("stageWaveCount", stageSummaries.size());
        summary.put("dynamicFilterStageCount", Math.max(0, stageSummaries.size() - 1));
        summary.put("executedStageCount", stageSummaries.size());
        summary.put("stageSummaries", List.copyOf(stageSummaries));
        summary.put("resultBuffer", resultBuffer.summary());
        return summary;
    }

    private Map<String, Object> stageSummary(String stageId,
                                             String sourceAlias,
                                             int rowCount,
                                             boolean lookupDriven) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("stageId", stageId);
        summary.put("sourceAlias", sourceAlias);
        summary.put("rowCount", rowCount);
        summary.put("lookupDriven", lookupDriven);
        summary.put("threadName", Thread.currentThread().getName());
        return summary;
    }

    private List<Map<String, Object>> executePrimarySql(DataServiceRuntimeDefinition runtimeDefinition,
                                                        DataServiceRuntimeSource primarySource,
                                                        Map<String, Object> params,
                                                        int queryTimeoutSeconds,
                                                        int maxResultRows) {
        return executeSql(
            primarySource,
            runtimeDefinition.definition().getSqlTemplate(),
            filterParamDefinitions(runtimeDefinition.definition().getSqlTemplate(), runtimeDefinition.params()),
            params,
            runtimeDefinition.fields().stream()
                .filter(field -> primarySource.source().getSourceAlias().equals(field.getSourceAlias()))
                .toList(),
            queryTimeoutSeconds,
            maxResultRows
        );
    }

    private List<Map<String, Object>> executePushedDownJoin(DataServiceRuntimeDefinition runtimeDefinition,
                                                            DataServiceRuntimeSource primarySource,
                                                            DataServiceRuntimeSource childSource,
                                                            PredefinedJoinConfig joinConfig,
                                                            Map<String, Object> params,
                                                            int queryTimeoutSeconds,
                                                            int maxResultRows) {
        String pushdownSql = buildPushedDownSql(runtimeDefinition, primarySource, childSource, joinConfig);
        List<DSParam> pushedDownParams = filterParamDefinitions(pushdownSql, runtimeDefinition.params());
        List<DSField> allFields = runtimeDefinition.fields().stream()
            .filter(field -> shouldExposeField(primarySource, field))
            .toList();
        return executeSql(primarySource, pushdownSql, pushedDownParams, params, allFields, queryTimeoutSeconds, maxResultRows);
    }

    private List<Map<String, Object>> executeChildLookup(DataServiceRuntimeDefinition runtimeDefinition,
                                                         DataServiceRuntimeSource childSource,
                                                         List<Map<String, Object>> parentRows,
                                                         PredefinedJoinConfig joinConfig,
                                                         int queryTimeoutSeconds,
                                                         int maxResultRows) {
        List<Object> joinValues = parentRows.stream()
            .map(row -> row.get(joinConfig.parentJoinField()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (joinValues.isEmpty()) {
            return List.of();
        }

        DSParam lookupParamDefinition = runtimeDefinition.params().stream()
            .filter(param -> joinConfig.lookupParam().equals(param.getParamName()))
            .findFirst()
            .orElseThrow(() -> new ServiceConfigInvalidException("跨库从源缺少 lookup 参数定义: " + joinConfig.lookupParam()));

        Map<String, Object> childParams = new LinkedHashMap<>();
        childParams.put(joinConfig.lookupParam(), joinValues);
        List<DSParam> childParamDefinitions = List.of(copyAsListParam(lookupParamDefinition));

        List<DSField> childFields = runtimeDefinition.fields().stream()
            .filter(field -> childSource.source().getSourceAlias().equals(field.getSourceAlias()))
            .toList();
        return executeSqlStreaming(childSource, joinConfig.childSqlTemplate(), childParamDefinitions, childParams, childFields,
            queryTimeoutSeconds, maxResultRows);
    }

    private boolean canPushDownToDatabase(DataServiceRuntimeSource primarySource,
                                          DataServiceRuntimeSource childSource,
                                          Map<String, Object> params,
                                          PredefinedJoinConfig joinConfig) {
        if (!Objects.equals(primarySource.connection().getId(), childSource.connection().getId())) {
            return false;
        }
        Object lookupParamValue = params.get(joinConfig.lookupParam());
        if (!(lookupParamValue instanceof Collection<?> collection) || collection.isEmpty()) {
            return false;
        }
        return primarySource.catalog() == null
            || childSource.catalog() == null
            || Objects.equals(primarySource.catalog().getCatalogValue(), childSource.catalog().getCatalogValue());
    }

    private String buildPushedDownSql(DataServiceRuntimeDefinition runtimeDefinition,
                                      DataServiceRuntimeSource primarySource,
                                      DataServiceRuntimeSource childSource,
                                      PredefinedJoinConfig joinConfig) {
        String primarySql = trimTrailingSemicolon(runtimeDefinition.definition().getSqlTemplate());
        String childSql = trimTrailingSemicolon(joinConfig.childSqlTemplate());
        String joinKeyword = "INNER".equals(joinConfig.joinType()) ? "inner join" : "left join";
        DSField parentJoinField = findFieldByFieldName(runtimeDefinition, primarySource.source().getSourceAlias(),
            joinConfig.parentJoinField());
        DSField childJoinField = findFieldByFieldName(runtimeDefinition, childSource.source().getSourceAlias(),
            joinConfig.childJoinField());

        String projection = runtimeDefinition.fields().stream()
            .filter(field -> shouldExposeField(primarySource, field))
            .map(field -> buildProjectionExpression(primarySource, childSource, field))
            .collect(Collectors.joining(",\n    "));

        return """
            select
                %s
            from (
                %s
            ) primary_src
            %s (
                %s
            ) child_src
              on primary_src.%s = child_src.%s
            """.formatted(
            projection,
            primarySql,
            joinKeyword,
            childSql,
            buildRuntimeColumnName(parentJoinField),
            buildRuntimeColumnName(childJoinField)
        );
    }

    private String buildProjectionExpression(DataServiceRuntimeSource primarySource,
                                             DataServiceRuntimeSource childSource,
                                             DSField field) {
        String tableAlias = primarySource.source().getSourceAlias().equals(field.getSourceAlias()) ? "primary_src" : "child_src";
        String runtimeColumn = buildRuntimeColumnName(field);
        return "%s.%s as %s_%s".formatted(
            tableAlias,
            runtimeColumn,
            field.getSourceAlias(),
            field.getSourceColumn()
        );
    }

    private List<DSParam> filterParamDefinitions(String sqlTemplate, List<DSParam> paramDefinitions) {
        Set<String> placeholders = QueryPlaceholderExtractor.extract(sqlTemplate);
        return paramDefinitions.stream()
            .filter(definition -> placeholders.contains(definition.getSqlPlaceholder()))
            .toList();
    }

    private void validateJoinFields(DataServiceRuntimeDefinition runtimeDefinition,
                                    DataServiceRuntimeSource primarySource,
                                    DataServiceRuntimeSource childSource,
                                    PredefinedJoinConfig joinConfig) {
        DSField parentJoinField = findFieldByFieldName(runtimeDefinition, primarySource.source().getSourceAlias(),
            joinConfig.parentJoinField());
        DSField childJoinField = findFieldByFieldName(runtimeDefinition, childSource.source().getSourceAlias(),
            joinConfig.childJoinField());
        if (!Boolean.TRUE.equals(parentJoinField.getJoinKey())) {
            throw new ServiceConfigInvalidException("主源关联字段必须显式标记 joinKey: " + joinConfig.parentJoinField());
        }
        if (!Boolean.TRUE.equals(childJoinField.getJoinKey())) {
            throw new ServiceConfigInvalidException("从源关联字段必须显式标记 joinKey: " + joinConfig.childJoinField());
        }
        if (!childJoinField.getSourceColumn().equals(joinConfig.lookupSourceColumn())) {
            throw new ServiceConfigInvalidException("从源 lookupSourceColumn 与关联字段列名不一致: " + joinConfig.lookupSourceColumn());
        }
    }

    private DSField findFieldByFieldName(DataServiceRuntimeDefinition runtimeDefinition,
                                         String sourceAlias,
                                         String fieldName) {
        return runtimeDefinition.fields().stream()
            .filter(field -> sourceAlias.equals(field.getSourceAlias()) && fieldName.equals(field.getFieldName()))
            .findFirst()
            .orElseThrow(() -> new ServiceConfigInvalidException(
                "未找到关联字段映射, sourceAlias=" + sourceAlias + ", fieldName=" + fieldName));
    }

    private String trimTrailingSemicolon(String sql) {
        if (sql == null) {
            return "";
        }
        String trimmed = sql.trim();
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
    }

    private String buildRuntimeColumnName(DSField field) {
        return field.getSourceAlias() + "_" + field.getSourceColumn();
    }

    private boolean shouldExposeField(DataServiceRuntimeSource primarySource, DSField field) {
        return !Boolean.TRUE.equals(field.getJoinKey())
            || primarySource.source().getSourceAlias().equals(field.getSourceAlias());
    }

    private List<Map<String, Object>> executeSql(DataServiceRuntimeSource runtimeSource,
                                                 String sqlTemplate,
                                                 List<DSParam> paramDefinitions,
                                                 Map<String, Object> params,
                                                 List<DSField> fields,
                                                 int queryTimeoutSeconds,
                                                 int maxResultRows) {
        sqlReadOnlyValidator.validate(sqlTemplate);
        BoundQuery boundQuery = queryParameterBinder.bind(sqlTemplate, paramDefinitions, params);
        List<Map<String, Object>> rawRows = namedParameterQueryExecutor.query(
            runtimeSource,
            boundQuery.sql(),
            boundQuery.params(),
            queryTimeoutSeconds,
            maxResultRows
        );
        return queryResultMapper.map(rawRows, fields);
    }

    private List<Map<String, Object>> executeSqlStreaming(DataServiceRuntimeSource runtimeSource,
                                                          String sqlTemplate,
                                                          List<DSParam> paramDefinitions,
                                                          Map<String, Object> params,
                                                          List<DSField> fields,
                                                          int queryTimeoutSeconds,
                                                          int maxResultRows) {
        sqlReadOnlyValidator.validate(sqlTemplate);
        BoundQuery boundQuery = queryParameterBinder.bind(sqlTemplate, paramDefinitions, params);
        List<Map<String, Object>> rawRows = new ArrayList<>();
        StreamingQueryRowHandler rowHandler = row -> rawRows.add(new LinkedHashMap<>(row));
        namedParameterQueryExecutor.streamQuery(
            runtimeSource,
            boundQuery.sql(),
            boundQuery.params(),
            queryTimeoutSeconds,
            DEFAULT_FETCH_SIZE,
            maxResultRows,
            rowHandler
        );
        return queryResultMapper.map(rawRows, fields);
    }

    private DSParam copyAsListParam(DSParam sourceParam) {
        DSParam copied = new DSParam();
        copied.setParamName(sourceParam.getParamName());
        copied.setDisplayName(sourceParam.getDisplayName());
        copied.setParamType("LIST");
        copied.setSqlPlaceholder(sourceParam.getSqlPlaceholder());
        copied.setRequired(sourceParam.getRequired());
        copied.setDefaultValue(sourceParam.getDefaultValue());
        copied.setSortOrder(sourceParam.getSortOrder());
        copied.setRemark(sourceParam.getRemark());
        return copied;
    }

    private static final class QueryPlaceholderExtractor {

        private QueryPlaceholderExtractor() {
        }

        private static Set<String> extract(String sql) {
            return SqlTemplateRenderer.extractPlaceholders(sql);
        }
    }

    public record PredefinedJoinExecutionResult(List<Map<String, Object>> rows,
                                                Map<String, Object> summary) {
    }
}
