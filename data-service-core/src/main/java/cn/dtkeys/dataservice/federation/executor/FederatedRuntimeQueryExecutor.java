package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import cn.dtkeys.dataservice.service.model.DSField;
import cn.dtkeys.dataservice.service.model.DSParam;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeDefinition;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeSource;
import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.query.executor.BoundQuery;
import cn.dtkeys.dataservice.query.executor.NamedParameterQueryExecutor;
import cn.dtkeys.dataservice.query.executor.QueryParameterBinder;
import cn.dtkeys.dataservice.query.executor.QueryResultMapper;
import cn.dtkeys.dataservice.query.executor.SqlReadOnlyValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class FederatedRuntimeQueryExecutor {

    private static final Pattern PARAMETER_PATTERN = Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");
    private static final String LOOKUP_PARAM_NAME = "federatedLookupValues";
    private static final int DEFAULT_PREVIEW_ROWS = 200;
    private static final int DEFAULT_FETCH_SIZE = 200;

    private final QueryParameterBinder queryParameterBinder;
    private final NamedParameterQueryExecutor namedParameterQueryExecutor;
    private final QueryResultMapper queryResultMapper;
    private final SqlReadOnlyValidator sqlReadOnlyValidator;

    public FederatedRuntimeQueryExecutor(QueryParameterBinder queryParameterBinder,
                                         NamedParameterQueryExecutor namedParameterQueryExecutor,
                                         QueryResultMapper queryResultMapper,
                                         SqlReadOnlyValidator sqlReadOnlyValidator) {
        this.queryParameterBinder = queryParameterBinder;
        this.namedParameterQueryExecutor = namedParameterQueryExecutor;
        this.queryResultMapper = queryResultMapper;
        this.sqlReadOnlyValidator = sqlReadOnlyValidator;
    }

    public FederatedRuntimeExecutionResult execute(DataServiceRuntimeDefinition runtimeDefinition,
                                                   FederatedParsedQuery parsedQuery,
                                                   FederatedPlan plan,
                                                   Map<String, Object> params,
                                                   int queryTimeoutSeconds,
                                                   int maxResultRows) {
        if (runtimeDefinition.sources().isEmpty()) {
            throw new ServiceConfigInvalidException("联邦 SQL 未配置可用来源");
        }

        List<Map<String, Object>> stageSummaries = new ArrayList<>();
        List<Map<String, Object>> mergedRows = List.of();
        DataServiceRuntimeSource primarySource = resolveRuntimeSource(runtimeDefinition, parsedQuery.sourceTables().get(0));
        List<Map<String, Object>> primaryRows = executeStage(
            runtimeDefinition,
            primarySource,
            parsedQuery,
            params,
            null,
            false,
            queryTimeoutSeconds,
            maxResultRows
        );
        stageSummaries.add(stageSummary("stage-1", primarySource.source().getSourceAlias(), primaryRows.size(), false));
        mergedRows = primaryRows;

        for (int index = 1; index < parsedQuery.sourceTables().size(); index++) {
            DataServiceRuntimeSource childSource = resolveRuntimeSource(runtimeDefinition, parsedQuery.sourceTables().get(index));
            List<Map<String, Object>> childRows = executeStage(
                runtimeDefinition,
                childSource,
                parsedQuery,
                params,
                mergedRows,
                true,
                queryTimeoutSeconds,
                maxResultRows
            );
            stageSummaries.add(stageSummary("stage-" + (index + 1), childSource.source().getSourceAlias(), childRows.size(), true));
            mergedRows = mergeRows(primarySource, childSource, mergedRows, childRows, parsedQuery.originalSql(), runtimeDefinition.fields());
        }

        List<Map<String, Object>> projectedRows = projectRows(mergedRows, parsedQuery, runtimeDefinition.fields());
        try (FederatedExecutionBuffer resultBuffer = new FederatedExecutionBuffer(
            Math.min(DEFAULT_PREVIEW_ROWS, maxResultRows),
            maxResultRows,
            Math.max(1, Math.min(DEFAULT_FETCH_SIZE, maxResultRows))
        )) {
            resultBuffer.appendRows(projectedRows);
            return new FederatedRuntimeExecutionResult(
                resultBuffer.previewRows(),
                buildSummary(plan, stageSummaries, resultBuffer.summary())
            );
        }
    }

    private List<Map<String, Object>> executeStage(DataServiceRuntimeDefinition runtimeDefinition,
                                                   DataServiceRuntimeSource runtimeSource,
                                                   FederatedParsedQuery parsedQuery,
                                                   Map<String, Object> requestParams,
                                                   List<Map<String, Object>> parentRows,
                                                   boolean lookupDriven,
                                                   int queryTimeoutSeconds,
                                                   int maxResultRows) {
        StageSql stageSql = buildStageSql(runtimeDefinition, runtimeSource, parsedQuery, parentRows, lookupDriven);
        sqlReadOnlyValidator.validate(stageSql.sql());
        BoundQuery boundQuery = queryParameterBinder.bind(stageSql.sql(), stageSql.paramDefinitions(), stageSql.params(requestParams));
        List<Map<String, Object>> rawRows = namedParameterQueryExecutor.query(
            runtimeSource,
            boundQuery.sql(),
            boundQuery.params(),
            queryTimeoutSeconds,
            maxResultRows
        );
        return queryResultMapper.map(rawRows, stageSql.sourceFields());
    }

    private StageSql buildStageSql(DataServiceRuntimeDefinition runtimeDefinition,
                                   DataServiceRuntimeSource runtimeSource,
                                   FederatedParsedQuery parsedQuery,
                                   List<Map<String, Object>> parentRows,
                                   boolean lookupDriven) {
        String sourceRef = currentSourceReference(runtimeSource);
        List<DSField> stageFields = stageFields(runtimeDefinition.fields(), runtimeSource);
        String projection = stageFields.stream()
            .map(field -> field.getSourceColumn() + " as " + runtimeSource.source().getSourceAlias() + "_" + field.getSourceColumn())
            .collect(Collectors.joining(", "));
        if (projection.isBlank()) {
            throw new ServiceConfigInvalidException("联邦来源缺少字段定义: " + sourceRef);
        }

        List<String> predicates = extractLocalPredicates(parsedQuery.whereClause(), runtimeSource, runtimeDefinition.sources());
        DSParam lookupParam = null;
        Collection<?> lookupValues = null;
        if (lookupDriven) {
            DSField joinKeyField = resolveJoinKeyField(runtimeDefinition.fields(), runtimeSource);
            lookupValues = extractJoinValues(parentRows, resolvePrimaryJoinField(runtimeDefinition.fields(), runtimeDefinition.sources()));
            if (!lookupValues.isEmpty()) {
                predicates.add(joinKeyField.getSourceColumn() + " in (:" + LOOKUP_PARAM_NAME + ")");
                lookupParam = listParam(LOOKUP_PARAM_NAME);
            }
        }
        if (lookupDriven && (lookupValues == null || lookupValues.isEmpty())) {
            return StageSql.empty(stageFields);
        }

        List<DSParam> allParams = new ArrayList<>(runtimeDefinition.params());
        if (lookupParam != null) {
            allParams.add(lookupParam);
        }
        StringBuilder sqlBuilder = new StringBuilder("select ").append(projection)
            .append(" from ").append(resolveSourceRelation(runtimeSource));
        if (!predicates.isEmpty()) {
            sqlBuilder.append(" where ").append(String.join(" and ", predicates));
        }
        sqlBuilder.append(" order by ").append(resolveJoinKeyField(runtimeDefinition.fields(), runtimeSource).getSourceColumn());
        String finalSql = sqlBuilder.toString();
        List<DSParam> usedParams = filterParamDefinitions(finalSql, allParams);
        return new StageSql(finalSql, usedParams, lookupValues, stageFields);
    }

    private List<String> extractLocalPredicates(String whereClause,
                                                DataServiceRuntimeSource runtimeSource,
                                                List<DataServiceRuntimeSource> allSources) {
        if (whereClause == null || whereClause.isBlank()) {
            return new ArrayList<>();
        }
        Set<String> currentNames = new LinkedHashSet<>();
        currentNames.add(normalize(runtimeSource.source().getSourceAlias()));
        currentNames.add(normalize(runtimeSource.source().getSourceValue()));
        Set<String> otherNames = allSources.stream()
            .flatMap(source -> java.util.stream.Stream.of(source.source().getSourceAlias(), source.source().getSourceValue()))
            .filter(Objects::nonNull)
            .map(this::normalize)
            .filter(name -> !currentNames.contains(name))
            .collect(Collectors.toSet());
        List<String> predicates = new ArrayList<>();
        for (String rawPredicate : whereClause.split("(?i)\\s+and\\s+")) {
            String predicate = rawPredicate.trim();
            if (predicate.isBlank()) {
                continue;
            }
            String normalized = normalize(predicate);
            boolean referencesCurrent = currentNames.stream().anyMatch(name -> normalized.contains(name + "."));
            boolean referencesOther = otherNames.stream().anyMatch(name -> normalized.contains(name + "."));
            if (referencesOther) {
                continue;
            }
            if (!referencesCurrent && !otherNames.isEmpty()) {
                continue;
            }
            String rewritten = predicate;
            for (String currentName : currentNames) {
                rewritten = rewritten.replaceAll("(?i)\\b" + Pattern.quote(currentName) + "\\.", "");
            }
            predicates.add(rewritten);
        }
        return predicates;
    }

    private List<Map<String, Object>> mergeRows(DataServiceRuntimeSource primarySource,
                                                DataServiceRuntimeSource childSource,
                                                List<Map<String, Object>> parentRows,
                                                List<Map<String, Object>> childRows,
                                                String originalSql,
                                                List<DSField> allFields) {
        String parentJoinField = resolveJoinKeyField(allFields, primarySource).getFieldName();
        String childJoinField = resolveJoinKeyField(allFields, childSource).getFieldName();
        boolean leftJoin = normalize(originalSql).contains(" left join ");
        Map<Object, List<Map<String, Object>>> childIndex = childRows.stream()
            .filter(row -> row.get(childJoinField) != null)
            .collect(Collectors.groupingBy(row -> row.get(childJoinField), LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> parentRow : parentRows) {
            Object joinValue = parentRow.get(parentJoinField);
            List<Map<String, Object>> matches = childIndex.get(joinValue);
            if (matches == null || matches.isEmpty()) {
                if (leftJoin) {
                    merged.add(new LinkedHashMap<>(parentRow));
                }
                continue;
            }
            for (Map<String, Object> childRow : matches) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>(parentRow);
                childRow.forEach(row::putIfAbsent);
                merged.add(row);
            }
        }
        return List.copyOf(merged);
    }

    private List<Map<String, Object>> projectRows(List<Map<String, Object>> rows,
                                                  FederatedParsedQuery parsedQuery,
                                                  List<DSField> fields) {
        List<String> fieldNames = parsedQuery.selectedFields().stream()
            .map(field -> resolveSelectedFieldName(field, fields))
            .toList();
        List<Map<String, Object>> projected = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            LinkedHashMap<String, Object> projectedRow = new LinkedHashMap<>();
            for (String fieldName : fieldNames) {
                projectedRow.put(fieldName, row.get(fieldName));
            }
            projected.add(projectedRow);
        }
        return List.copyOf(projected);
    }

    private String resolveSelectedFieldName(String selectedField, List<DSField> fields) {
        String normalizedField = normalize(stripAlias(selectedField));
        String sourceName = null;
        String columnName = normalizedField;
        if (normalizedField.contains(".")) {
            sourceName = normalizedField.substring(0, normalizedField.indexOf('.'));
            columnName = normalizedField.substring(normalizedField.indexOf('.') + 1);
        }
        final String expectedSourceName = sourceName;
        final String expectedColumnName = columnName;
        return fields.stream()
            .filter(field -> expectedSourceName == null
                || normalize(field.getSourceAlias()).equals(expectedSourceName)
                || normalize(field.getSourceColumn()).equals(expectedSourceName)
                || normalize(field.getSourceColumn()).equals(expectedSourceName))
            .filter(field -> normalize(field.getSourceColumn()).equals(expectedColumnName))
            .map(DSField::getFieldName)
            .findFirst()
            .orElseThrow(() -> new ServiceConfigInvalidException("联邦 SQL 选择列未在字段映射中定义: " + selectedField));
    }

    private String stripAlias(String selectedField) {
        String normalized = selectedField.trim();
        int asIndex = normalize(normalized).indexOf(" as ");
        if (asIndex >= 0) {
            return normalized.substring(0, asIndex).trim();
        }
        return normalized;
    }

    private Collection<?> extractJoinValues(List<Map<String, Object>> parentRows, DSField primaryJoinField) {
        return parentRows.stream()
            .map(row -> row.get(primaryJoinField.getFieldName()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    private DSField resolvePrimaryJoinField(List<DSField> fields, List<DataServiceRuntimeSource> sources) {
        return resolveJoinKeyField(fields, sources.get(0));
    }

    private DSField resolveJoinKeyField(List<DSField> fields, DataServiceRuntimeSource runtimeSource) {
        String configuredJoinKey = runtimeSource.source().getJoinKey();
        if (configuredJoinKey != null && !configuredJoinKey.isBlank()) {
            String normalizedJoinKey = normalize(configuredJoinKey);
            return sourceFields(fields, runtimeSource).stream()
                .filter(field -> normalize(field.getSourceColumn()).equals(normalizedJoinKey))
                .findFirst()
                .orElseGet(() -> syntheticJoinKeyField(runtimeSource, configuredJoinKey));
        }
        return sourceFields(fields, runtimeSource).stream()
            .filter(field -> Boolean.TRUE.equals(field.getJoinKey()))
            .findFirst()
            .orElseThrow(() -> new ServiceConfigInvalidException("联邦来源缺少 joinKey 字段: "
                + runtimeSource.source().getSourceAlias()));
    }

    private DSField syntheticJoinKeyField(DataServiceRuntimeSource runtimeSource, String joinKeyColumn) {
        DSField field = new DSField();
        field.setSourceAlias(runtimeSource.source().getSourceAlias());
        field.setSourceColumn(joinKeyColumn);
        field.setFieldName(joinKeyColumn);
        field.setDisplayName(joinKeyColumn);
        field.setJoinKey(true);
        return field;
    }

    private List<DSField> sourceFields(List<DSField> fields, DataServiceRuntimeSource runtimeSource) {
        return fields.stream()
            .filter(field -> normalize(field.getSourceAlias()).equals(normalize(runtimeSource.source().getSourceAlias())))
            .toList();
    }

    private List<DSField> stageFields(List<DSField> fields, DataServiceRuntimeSource runtimeSource) {
        List<DSField> sourceFields = new ArrayList<>(sourceFields(fields, runtimeSource));
        DSField joinKeyField = resolveJoinKeyField(fields, runtimeSource);
        boolean joinKeyPresent = sourceFields.stream()
            .anyMatch(field -> normalize(field.getSourceColumn()).equals(normalize(joinKeyField.getSourceColumn())));
        if (!joinKeyPresent) {
            sourceFields.add(joinKeyField);
        }
        return List.copyOf(sourceFields);
    }

    private List<DSParam> filterParamDefinitions(String sql, List<DSParam> allParams) {
        if (sql == null || sql.isBlank()) {
            return List.of();
        }
        Set<String> placeholders = extractPlaceholders(sql);
        return allParams.stream()
            .filter(param -> placeholders.contains(param.getSqlPlaceholder()))
            .toList();
    }

    private Set<String> extractPlaceholders(String sql) {
        Matcher matcher = PARAMETER_PATTERN.matcher(sql);
        Set<String> placeholders = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
    }

    private DSParam listParam(String placeholder) {
        DSParam param = new DSParam();
        param.setParamName(placeholder);
        param.setDisplayName(placeholder);
        param.setParamType("LIST");
        param.setSqlPlaceholder(placeholder);
        param.setRequired(true);
        param.setSortOrder(999);
        return param;
    }

    private DataServiceRuntimeSource resolveRuntimeSource(DataServiceRuntimeDefinition runtimeDefinition, String sourceName) {
        String normalized = normalize(sourceName);
        return runtimeDefinition.sources().stream()
            .filter(source -> normalize(source.source().getSourceAlias()).equals(normalized)
                || normalize(source.source().getSourceValue()).equals(normalized))
            .findFirst()
            .orElseThrow(() -> new ServiceConfigInvalidException("联邦 SQL 来源未配置: " + sourceName));
    }

    private Map<String, Object> buildSummary(FederatedPlan plan,
                                             List<Map<String, Object>> stageSummaries,
                                             Map<String, Object> resultBufferSummary) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", "LOOKUP");
        summary.put("maxParallelism", 1);
        summary.put("plannedMaxParallelism", plan.executionProfile().getOrDefault("maxParallelism", 1));
        summary.put("stageWaveCount", stageSummaries.size());
        summary.put("dynamicFilterStageCount", Math.max(0, stageSummaries.size() - 1));
        summary.put("executedStageCount", stageSummaries.size());
        summary.put("stageSummaries", List.copyOf(stageSummaries));
        summary.put("optimizationDecisions", plan.optimizationDecisions());
        summary.put("resultBuffer", resultBufferSummary);
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

    private String currentSourceReference(DataServiceRuntimeSource runtimeSource) {
        return runtimeSource.source().getSourceAlias() == null || runtimeSource.source().getSourceAlias().isBlank()
            ? runtimeSource.source().getSourceValue()
            : runtimeSource.source().getSourceAlias();
    }

    private String resolveSourceRelation(DataServiceRuntimeSource runtimeSource) {
        String sourceValue = runtimeSource.source().getSourceValue();
        if (sourceValue == null || sourceValue.isBlank() || sourceValue.contains(".")) {
            return sourceValue;
        }
        if (runtimeSource.catalog() == null || runtimeSource.catalog().getCatalogValue() == null
            || runtimeSource.catalog().getCatalogValue().isBlank()) {
            return sourceValue;
        }
        String catalogType = normalize(runtimeSource.catalog().getCatalogType());
        String dbType = normalize(runtimeSource.connection().getDbType());
        if ("schema".equals(catalogType) || ("database".equals(catalogType) && "mysql".equals(dbType))) {
            return runtimeSource.catalog().getCatalogValue() + "." + sourceValue;
        }
        return sourceValue;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record FederatedRuntimeExecutionResult(List<Map<String, Object>> rows,
                                                  Map<String, Object> summary) {
    }

    private record StageSql(String sql,
                            List<DSParam> paramDefinitions,
                            Collection<?> lookupValues,
                            List<DSField> sourceFields) {

        private static StageSql empty(List<DSField> sourceFields) {
            return new StageSql("select 1 where 1 = 0", List.of(), List.of(), sourceFields);
        }

        private Map<String, Object> params(Map<String, Object> requestParams) {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            Map<String, Object> safeRequestParams = requestParams == null ? Map.of() : requestParams;
            for (DSParam paramDefinition : paramDefinitions) {
                if (safeRequestParams.containsKey(paramDefinition.getParamName())) {
                    params.put(paramDefinition.getParamName(), safeRequestParams.get(paramDefinition.getParamName()));
                }
            }
            if (lookupValues != null && !lookupValues.isEmpty()) {
                params.put(LOOKUP_PARAM_NAME, lookupValues);
            }
            return Map.copyOf(params);
        }
    }
}
