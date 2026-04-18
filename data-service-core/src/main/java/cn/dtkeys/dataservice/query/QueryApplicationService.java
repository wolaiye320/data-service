package cn.dtkeys.dataservice.query;

import cn.dtkeys.dataservice.common.context.OperatorContext;
import cn.dtkeys.dataservice.common.context.TraceContext;
import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.common.exception.ResourceLimitExceededException;
import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeDefinition;
import cn.dtkeys.dataservice.federation.executor.FederatedRuntimeQueryExecutor;
import cn.dtkeys.dataservice.federation.executor.PredefinedJoinQueryExecutor;
import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.optimizer.FederatedSqlOptimizer;
import cn.dtkeys.dataservice.federation.parser.FederatedSqlParser;
import cn.dtkeys.dataservice.federation.planner.FederatedSqlPlanner;
import cn.dtkeys.dataservice.audit.AuditEvent;
import cn.dtkeys.dataservice.audit.AuditLogService;
import cn.dtkeys.dataservice.query.cache.CacheKeyGenerator;
import cn.dtkeys.dataservice.query.cache.CacheLookupResult;
import cn.dtkeys.dataservice.query.cache.CachePolicyResolver;
import cn.dtkeys.dataservice.query.cache.QueryCache;
import cn.dtkeys.dataservice.query.cache.QueryCacheValue;
import cn.dtkeys.dataservice.query.cache.ResolvedCachePolicy;
import cn.dtkeys.dataservice.query.executor.BoundQuery;
import cn.dtkeys.dataservice.query.executor.NamedParameterQueryExecutor;
import cn.dtkeys.dataservice.query.executor.QueryParameterBinder;
import cn.dtkeys.dataservice.query.executor.QueryResultMapper;
import cn.dtkeys.dataservice.query.executor.SqlReadOnlyValidator;
import cn.dtkeys.dataservice.audit.AuditAction;
import cn.dtkeys.dataservice.query.protection.ResourceProtectionProperties;
import cn.dtkeys.dataservice.query.protection.ResourceProtectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一查询应用服务，负责编排发布态定义加载、校验、执行和结果映射。
 */
@Service
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
public class QueryApplicationService {

    private final ServiceDefinitionLoader serviceDefinitionLoader;
    private final SqlReadOnlyValidator sqlReadOnlyValidator;
    private final QueryParameterBinder queryParameterBinder;
    private final NamedParameterQueryExecutor namedParameterQueryExecutor;
    private final QueryResultMapper queryResultMapper;
    private final ResourceProtectionService resourceProtectionService;
    private final ResourceProtectionProperties resourceProtectionProperties;
    private final AuditLogService auditLogService;
    private final QueryCache queryCache;
    private final CacheKeyGenerator cacheKeyGenerator;
    private final CachePolicyResolver cachePolicyResolver;
    private final PredefinedJoinQueryExecutor predefinedJoinQueryExecutor;
    private final FederatedRuntimeQueryExecutor federatedRuntimeQueryExecutor;
    private final FederatedSqlParser federatedSqlParser;
    private final FederatedSqlPlanner federatedSqlPlanner;
    private final FederatedSqlOptimizer federatedSqlOptimizer;

    public QueryApplicationService(ServiceDefinitionLoader serviceDefinitionLoader,
                                   SqlReadOnlyValidator sqlReadOnlyValidator,
                                   QueryParameterBinder queryParameterBinder,
                                   NamedParameterQueryExecutor namedParameterQueryExecutor,
                                   QueryResultMapper queryResultMapper,
                                   ResourceProtectionService resourceProtectionService,
                                   ResourceProtectionProperties resourceProtectionProperties,
                                   AuditLogService auditLogService,
                                   QueryCache queryCache,
                                   CacheKeyGenerator cacheKeyGenerator,
                                   CachePolicyResolver cachePolicyResolver,
                                   PredefinedJoinQueryExecutor predefinedJoinQueryExecutor,
                                   FederatedRuntimeQueryExecutor federatedRuntimeQueryExecutor) {
        this.serviceDefinitionLoader = serviceDefinitionLoader;
        this.sqlReadOnlyValidator = sqlReadOnlyValidator;
        this.queryParameterBinder = queryParameterBinder;
        this.namedParameterQueryExecutor = namedParameterQueryExecutor;
        this.queryResultMapper = queryResultMapper;
        this.resourceProtectionService = resourceProtectionService;
        this.resourceProtectionProperties = resourceProtectionProperties;
        this.auditLogService = auditLogService;
        this.queryCache = queryCache;
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.cachePolicyResolver = cachePolicyResolver;
        this.predefinedJoinQueryExecutor = predefinedJoinQueryExecutor;
        this.federatedRuntimeQueryExecutor = federatedRuntimeQueryExecutor;
        this.federatedSqlParser = new FederatedSqlParser();
        this.federatedSqlPlanner = new FederatedSqlPlanner();
        this.federatedSqlOptimizer = new FederatedSqlOptimizer();
    }

    /**
     * 执行统一查询。
     *
     * @param command 查询命令
     * @return 查询结果与元信息
     */
    public QueryExecutionResult execute(QueryExecutionCommand command) {
        return resourceProtectionService.executeWithinConcurrencyLimit(() -> doExecute(command));
    }

    private QueryExecutionResult doExecute(QueryExecutionCommand command) {
        if (command == null || command.serviceCode() == null || command.serviceCode().isBlank()) {
            throw new ParamInvalidException("serviceCode 不能为空");
        }
        if (!command.batchParams().isEmpty() && !command.params().isEmpty()) {
            throw new ParamInvalidException("params 与 batchParams 不能同时传入");
        }

        long startTime = System.currentTimeMillis();
        DataServiceRuntimeDefinition runtimeDefinition = serviceDefinitionLoader.loadPublished(command.serviceCode());
        validateSingleSource(runtimeDefinition);

        boolean federatedExecution = isFederatedExecution(runtimeDefinition);
        int queryTimeoutSeconds = resolveQueryTimeoutSeconds(runtimeDefinition, federatedExecution);
        int maxResultRows = resolveMaxResultRows(runtimeDefinition);
        ResolvedCachePolicy cachePolicy = cachePolicyResolver.resolve(
            runtimeDefinition.definition().getServiceCode(),
            runtimeDefinition.definition().getVersion(),
            runtimeDefinition.cachePolicy()
        );
        if (federatedExecution) {
            resourceProtectionService.validateFederatedQueryTimeout(queryTimeoutSeconds);
        } else {
            resourceProtectionService.validateQueryTimeout(queryTimeoutSeconds);
        }
        sqlReadOnlyValidator.validate(runtimeDefinition.definition().getSqlTemplate());

        boolean batchMode = !command.batchParams().isEmpty();
        BatchExecutionPayload batchExecutionPayload = batchMode
            ? executeBatch(runtimeDefinition, command.batchParams(), queryTimeoutSeconds, maxResultRows, cachePolicy)
            : BatchExecutionPayload.single(executeSingle(runtimeDefinition, command.params(), queryTimeoutSeconds, maxResultRows,
                cachePolicy));

        long durationMs = System.currentTimeMillis() - startTime;
        String operator = resolveOperator(command);
        auditLogService.record(AuditEvent.of(
            AuditAction.EXECUTE_DATA_SERVICE_QUERY.name(),
            runtimeDefinition.definition().getId(),
            runtimeDefinition.sources().isEmpty() ? null : runtimeDefinition.sources().get(0).source().getConnectionId(),
            "DATA_SERVICE",
            runtimeDefinition.definition().getServiceCode(),
            operator,
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            batchMode ? "执行批量统一查询" : "执行统一查询",
            buildAuditDetail(runtimeDefinition, batchExecutionPayload.rows().size(), durationMs, batchMode,
                batchMode ? command.batchParams().size() : 1, batchExecutionPayload.cacheHitCount(),
                batchExecutionPayload.additionalMeta())
        ));

        return new QueryExecutionResult(
            batchExecutionPayload.rows(),
            batchMode,
            batchExecutionPayload.batchResults(),
            buildMeta(runtimeDefinition, durationMs, batchExecutionPayload.rows().size(), operator, batchMode,
                batchMode ? command.batchParams().size() : 1, batchExecutionPayload.cacheHitCount(),
                batchExecutionPayload.additionalMeta())
        );
    }

    private void validateSingleSource(DataServiceRuntimeDefinition runtimeDefinition) {
        if (runtimeDefinition.sources() == null || runtimeDefinition.sources().isEmpty()) {
            throw new ServiceConfigInvalidException("数据服务未配置可用查询来源");
        }
    }

    private int resolveQueryTimeoutSeconds(DataServiceRuntimeDefinition runtimeDefinition, boolean federatedExecution) {
        Integer serviceTimeout = federatedExecution
            ? runtimeDefinition.definition().getFederatedQueryTimeoutSeconds()
            : runtimeDefinition.definition().getQueryTimeoutSeconds();
        if (serviceTimeout == null || serviceTimeout <= 0) {
            return federatedExecution
                ? resourceProtectionProperties.getFederatedQueryTimeoutSeconds()
                : resourceProtectionProperties.getQueryTimeoutSeconds();
        }
        return serviceTimeout;
    }

    private int resolveMaxResultRows(DataServiceRuntimeDefinition runtimeDefinition) {
        Integer maxResultRows = runtimeDefinition.definition().getMaxResultRows();
        if (maxResultRows == null || maxResultRows <= 0) {
            return resourceProtectionProperties.getMaxResultRows();
        }
        return maxResultRows;
    }

    private SingleExecutionPayload executeSingle(DataServiceRuntimeDefinition runtimeDefinition,
                                                 Map<String, Object> params,
                                                 int queryTimeoutSeconds,
                                                 int maxResultRows,
                                                 ResolvedCachePolicy cachePolicy) {
        Integer serviceMaxResultRows = runtimeDefinition.definition().getMaxResultRows();
        Map<String, Object> safeParams = params == null ? Map.of() : Map.copyOf(params);
        if (cachePolicy.enabled()) {
            String cacheKey = cacheKeyGenerator.generate(
                runtimeDefinition.definition().getServiceCode(),
                runtimeDefinition.definition().getVersion(),
                safeParams
            );
            CacheLookupResult lookupResult = queryCache.get(cacheKey);
            if (lookupResult.hit()) {
                return new SingleExecutionPayload(copyRows(lookupResult.value().rows()), true, Map.of());
            }
        }
        SingleExecutionPayload executionPayload = isFederatedExecution(runtimeDefinition)
            ? executeFederated(runtimeDefinition, safeParams, queryTimeoutSeconds, maxResultRows)
            : new SingleExecutionPayload(executeSingleSource(runtimeDefinition, safeParams, queryTimeoutSeconds, maxResultRows),
                false, Map.of());
        List<Map<String, Object>> rawRows = executionPayload.rows();
        if (serviceMaxResultRows != null && serviceMaxResultRows > 0 && rawRows.size() > serviceMaxResultRows) {
            throw new ResourceLimitExceededException("结果集行数超过服务限制: " + serviceMaxResultRows);
        }
        resourceProtectionService.validateResultRows(rawRows.size());
        List<Map<String, Object>> mappedRows = isFederatedExecution(runtimeDefinition)
            ? rawRows
            : queryResultMapper.map(rawRows, runtimeDefinition.fields());
        if (cachePolicy.enabled()) {
            String cacheKey = cacheKeyGenerator.generate(
                runtimeDefinition.definition().getServiceCode(),
                runtimeDefinition.definition().getVersion(),
                safeParams
            );
            queryCache.put(cacheKey, new QueryCacheValue(copyRows(mappedRows), Map.of("rowCount", mappedRows.size())),
                cachePolicy.ttlSeconds());
        }
        return new SingleExecutionPayload(mappedRows, false, executionPayload.additionalMeta());
    }

    private List<Map<String, Object>> executeSingleSource(DataServiceRuntimeDefinition runtimeDefinition,
                                                          Map<String, Object> params,
                                                          int queryTimeoutSeconds,
                                                          int maxResultRows) {
        BoundQuery boundQuery = queryParameterBinder.bind(
            runtimeDefinition.definition().getSqlTemplate(),
            runtimeDefinition.params(),
            params
        );
        return namedParameterQueryExecutor.query(
            runtimeDefinition.sources().get(0),
            boundQuery.sql(),
            boundQuery.params(),
            queryTimeoutSeconds,
            maxResultRows
        );
    }

    private BatchExecutionPayload executeBatch(DataServiceRuntimeDefinition runtimeDefinition,
                                               List<Map<String, Object>> batchParams,
                                               int queryTimeoutSeconds,
                                               int maxResultRows,
                                               ResolvedCachePolicy cachePolicy) {
        resourceProtectionService.validateBatchSize(batchParams.size());
        int serviceBatchLimit = resolveBatchSizeLimit(runtimeDefinition);
        if (batchParams.size() > serviceBatchLimit) {
            throw new ParamInvalidException("批量条数超过服务限制: " + serviceBatchLimit);
        }

        List<Map<String, Object>> mergedRows = new ArrayList<>();
        List<Map<String, Object>> batchResults = new ArrayList<>(batchParams.size());
        int cacheHitCount = 0;
        Map<String, Object> lastAdditionalMeta = Map.of();
        for (int i = 0; i < batchParams.size(); i++) {
            Map<String, Object> itemParams = batchParams.get(i);
            SingleExecutionPayload executionPayload = executeSingle(runtimeDefinition, itemParams, queryTimeoutSeconds,
                maxResultRows, cachePolicy);
            mergedRows.addAll(executionPayload.rows());
            batchResults.add(buildBatchResultItem(i, itemParams, executionPayload.rows(), executionPayload.cacheHit(),
                executionPayload.additionalMeta()));
            if (executionPayload.cacheHit()) {
                cacheHitCount++;
            }
            if (!executionPayload.additionalMeta().isEmpty()) {
                lastAdditionalMeta = executionPayload.additionalMeta();
            }
        }
        resourceProtectionService.validateResultRows(mergedRows.size());
        return new BatchExecutionPayload(List.copyOf(mergedRows), List.copyOf(batchResults), cacheHitCount, lastAdditionalMeta);
    }

    private int resolveBatchSizeLimit(DataServiceRuntimeDefinition runtimeDefinition) {
        Integer serviceBatchLimit = runtimeDefinition.definition().getMaxBatchSize();
        if (serviceBatchLimit == null || serviceBatchLimit <= 0) {
            return resourceProtectionProperties.getMaxBatchSize();
        }
        return serviceBatchLimit;
    }

    private Map<String, Object> buildMeta(DataServiceRuntimeDefinition runtimeDefinition,
                                         long durationMs,
                                         int rowCount,
                                         String operator,
                                         boolean batchMode,
                                         int batchSize,
                                         int cacheHitCount,
                                         Map<String, Object> additionalMeta) {
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        meta.put("serviceCode", runtimeDefinition.definition().getServiceCode());
        meta.put("version", runtimeDefinition.definition().getVersion());
        meta.put("durationMs", durationMs);
        meta.put("rowCount", rowCount);
        meta.put("batch", batchMode);
        meta.put("batchSize", batchSize);
        meta.put("cacheHit", cacheHitCount > 0);
        meta.put("cacheHitCount", cacheHitCount);
        meta.put("operator", operator);
        additionalMeta.forEach(meta::put);
        TraceContext.getTraceId().ifPresent(traceId -> meta.put("traceId", traceId));
        return meta;
    }

    private Map<String, Object> buildAuditDetail(DataServiceRuntimeDefinition runtimeDefinition,
                                                 int rowCount,
                                                 long durationMs,
                                                 boolean batchMode,
                                                 int batchSize,
                                                 int cacheHitCount,
                                                 Map<String, Object> additionalMeta) {
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        detail.put("serviceCode", runtimeDefinition.definition().getServiceCode());
        detail.put("serviceVersion", runtimeDefinition.definition().getVersion());
        detail.put("rowCount", rowCount);
        detail.put("batch", batchMode);
        detail.put("batchSize", batchSize);
        detail.put("durationMs", durationMs);
        detail.put("cacheHit", cacheHitCount > 0);
        detail.put("cacheHitCount", cacheHitCount);
        additionalMeta.forEach(detail::put);
        TraceContext.getTraceId().ifPresent(traceId -> detail.put("traceId", traceId));
        return detail;
    }

    private String resolveOperator(QueryExecutionCommand command) {
        if (command.operator() == null || command.operator().isBlank()) {
            return "SYSTEM";
        }
        return command.operator().trim();
    }

    private Map<String, Object> buildBatchResultItem(int index,
                                                     Map<String, Object> params,
                                                     List<Map<String, Object>> rows,
                                                     boolean cacheHit,
                                                     Map<String, Object> additionalMeta) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("index", index);
        item.put("params", params == null ? Map.of() : Map.copyOf(params));
        item.put("rowCount", rows.size());
        item.put("cacheHit", cacheHit);
        item.put("rows", rows);
        additionalMeta.forEach(item::put);
        return item;
    }

    private List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> copiedRows = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            copiedRows.add(Map.copyOf(row));
        }
        return List.copyOf(copiedRows);
    }

    private SingleExecutionPayload executeFederated(DataServiceRuntimeDefinition runtimeDefinition,
                                                    Map<String, Object> params,
                                                    int queryTimeoutSeconds,
                                                    int maxResultRows) {
        if ("FEDERATED_SQL".equalsIgnoreCase(runtimeDefinition.definition().getSqlType())) {
            FederatedParsedQuery parsedQuery = federatedSqlParser.parse(runtimeDefinition.definition().getSqlTemplate());
            FederatedPlan optimizedPlan = federatedSqlOptimizer.optimize(federatedSqlPlanner.plan(parsedQuery));
            FederatedRuntimeQueryExecutor.FederatedRuntimeExecutionResult result = federatedRuntimeQueryExecutor.execute(
                runtimeDefinition,
                parsedQuery,
                optimizedPlan,
                params,
                queryTimeoutSeconds,
                maxResultRows
            );
            return new SingleExecutionPayload(result.rows(), false, Map.of(
                "federatedExecution", result.summary(),
                "executionMode", runtimeDefinition.definition().getExecutionMode(),
                "sqlType", runtimeDefinition.definition().getSqlType()
            ));
        }
        PredefinedJoinQueryExecutor.PredefinedJoinExecutionResult result = predefinedJoinQueryExecutor.executeWithSummary(
            runtimeDefinition,
            params,
            queryTimeoutSeconds,
            maxResultRows
        );
        return new SingleExecutionPayload(result.rows(), false, Map.of(
            "federatedExecution", result.summary(),
            "executionMode", runtimeDefinition.definition().getExecutionMode(),
            "sqlType", runtimeDefinition.definition().getSqlType()
        ));
    }

    private boolean isFederatedExecution(DataServiceRuntimeDefinition runtimeDefinition) {
        return runtimeDefinition.sources().size() > 1
            || "FEDERATED_QUERY".equalsIgnoreCase(runtimeDefinition.definition().getServiceType())
            || "FEDERATED_SQL".equalsIgnoreCase(runtimeDefinition.definition().getSqlType())
            || "REMOTE_PLUS_LOCAL".equalsIgnoreCase(runtimeDefinition.definition().getExecutionMode());
    }

    private record SingleExecutionPayload(List<Map<String, Object>> rows,
                                          boolean cacheHit,
                                          Map<String, Object> additionalMeta) {
    }

    private record BatchExecutionPayload(List<Map<String, Object>> rows,
                                         List<Map<String, Object>> batchResults,
                                         int cacheHitCount,
                                         Map<String, Object> additionalMeta) {

        private static BatchExecutionPayload single(SingleExecutionPayload payload) {
            return new BatchExecutionPayload(payload.rows(), List.of(), payload.cacheHit() ? 1 : 0, payload.additionalMeta());
        }
    }
}
