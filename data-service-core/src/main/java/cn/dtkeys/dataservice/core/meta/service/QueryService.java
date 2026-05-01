package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.FederatedQueryExecutionException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.QueryParamValidationException;
import cn.dtkeys.dataservice.core.error.ResourceConflictException;
import cn.dtkeys.dataservice.core.error.ResourceNotFoundException;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsServiceRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsServiceVersionRepository;
import cn.dtkeys.dataservice.core.meta.web.request.QueryRequest;
import cn.dtkeys.dataservice.core.meta.web.response.QueryResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一编排已发布数据服务的正式查询执行，负责发布态校验、访问控制、参数绑定、缓存、
 * 单源或联邦执行分发、结果保护与审计记录。
 */
@Service
public class QueryService {

    private final DsServiceRepository serviceRepository;
    private final DsServiceVersionRepository serviceVersionRepository;
    private final QueryRequestContextService queryRequestContextService;
    private final QueryParamBindingService queryParamBindingService;
    private final SingleSourceQueryExecutor singleSourceQueryExecutor;
    private final FederatedQueryExecutor federatedQueryExecutor;
    private final QueryResponseAssembler queryResponseAssembler;
    private final QueryCacheKeyService queryCacheKeyService;
    private final QueryResultCache queryResultCache;
    private final QueryResultGuardService queryResultGuardService;
    private final QueryBatchGuardService queryBatchGuardService;
    private final QueryAccessGuardService queryAccessGuardService;
    private final QueryTenantGuardService queryTenantGuardService;
    private final AuditService auditService;

    public QueryService(DsServiceRepository serviceRepository,
                        DsServiceVersionRepository serviceVersionRepository,
                        QueryRequestContextService queryRequestContextService,
                        QueryParamBindingService queryParamBindingService,
                        SingleSourceQueryExecutor singleSourceQueryExecutor,
                        FederatedQueryExecutor federatedQueryExecutor,
                        QueryResponseAssembler queryResponseAssembler,
                        QueryCacheKeyService queryCacheKeyService,
                        QueryResultCache queryResultCache,
                        QueryResultGuardService queryResultGuardService,
                        QueryBatchGuardService queryBatchGuardService,
                        QueryAccessGuardService queryAccessGuardService,
                        QueryTenantGuardService queryTenantGuardService,
                        AuditService auditService) {
        this.serviceRepository = serviceRepository;
        this.serviceVersionRepository = serviceVersionRepository;
        this.queryRequestContextService = queryRequestContextService;
        this.queryParamBindingService = queryParamBindingService;
        this.singleSourceQueryExecutor = singleSourceQueryExecutor;
        this.federatedQueryExecutor = federatedQueryExecutor;
        this.queryResponseAssembler = queryResponseAssembler;
        this.queryCacheKeyService = queryCacheKeyService;
        this.queryResultCache = queryResultCache;
        this.queryResultGuardService = queryResultGuardService;
        this.queryBatchGuardService = queryBatchGuardService;
        this.queryAccessGuardService = queryAccessGuardService;
        this.queryTenantGuardService = queryTenantGuardService;
        this.auditService = auditService;
    }

    /**
     * 执行正式查询请求，并按输入顺序返回单项结果。
     */
    public QueryResponse execute(QueryRequest request, String traceId) {
        QueryRequestContextService.NormalizedQueryRequestContext requestContext =
                queryRequestContextService.normalize(request.requestContext(), traceId);
        long startedAt = System.currentTimeMillis();
        DsServiceRecord service = null;
        DsServiceVersionRecord version = null;
        try {
            // 先校验服务发布态，再阻断批量、访问与租户越权请求，避免无效请求进入执行阶段。
            service = requirePublishedService(request.serviceCode());
            version = requirePublishedVersion(service);
            queryBatchGuardService.validate(service, request.inputs());
            queryAccessGuardService.validate(requestContext);
            queryTenantGuardService.validate(service, requestContext);

            List<QueryResponse.QueryResultItem> items = buildItems(service, version, request.inputs(), requestContext);
            long elapsedMs = System.currentTimeMillis() - startedAt;
            QueryResponse response = queryResponseAssembler.successResponse(service, version, requestContext, items, elapsedMs);

            // 审计与主链路同属正式查询闭环，成功响应返回前必须记录执行结果。
            recordQueryAudit(service, request, requestContext, response, null, elapsedMs, traceId);
            return response;
        } catch (RuntimeException ex) {
            long elapsedMs = System.currentTimeMillis() - startedAt;

            // 失败路径也必须尝试落审计，避免查询异常绕过核心审计链路。
            recordQueryAudit(service, request, requestContext, null, ex, elapsedMs, traceId);
            throw ex;
        }
    }

    private List<QueryResponse.QueryResultItem> buildItems(DsServiceRecord service,
                                                           DsServiceVersionRecord version,
                                                           List<QueryRequest.QueryInput> inputs,
                                                           QueryRequestContextService.NormalizedQueryRequestContext requestContext) {
        return java.util.stream.IntStream.range(0, inputs.size())
                .mapToObj(index -> buildItem(index, service, version, inputs.get(index), requestContext))
                .toList();
    }

    private QueryResponse.QueryResultItem buildItem(int index,
                                                    DsServiceRecord service,
                                                    DsServiceVersionRecord version,
                                                    QueryRequest.QueryInput input,
                                                    QueryRequestContextService.NormalizedQueryRequestContext requestContext) {
        try {
            QueryParamBindingService.BoundQueryParams boundParams =
                    queryParamBindingService.bind(version.getParamSnapshotJson(), input == null ? Map.of() : input.params());
            QueryCacheKeyService.QueryCacheKeySnapshot cacheKeySnapshot =
                    queryCacheKeyService.build(service, version, boundParams.params(), requestContext);

            // 缓存命中后仍需走结果保护校验，避免历史缓存绕过当前行数或资源限制。
            if (cacheKeySnapshot.cacheEnabled()) {
                QueryResultCache.CacheHit cacheHit = queryResultCache.get(
                        cacheKeySnapshot.cacheKey(),
                        cacheKeySnapshot.ttlSeconds()
                );
                if (cacheHit.hit()) {
                    queryResultGuardService.validate(service, cacheHit.rows(), cacheHit.diagnosticSummary(), "CACHE_HIT");
                    return queryResponseAssembler.successItem(
                            index,
                            cacheHit.rows(),
                            0L,
                            true,
                            enrichDiagnosticSummary(cacheHit.diagnosticSummary(), cacheKeySnapshot, null, null, true)
                    );
                }

                // 联邦与单源执行器严格按发布快照的 sqlType 分流，不能在运行期混用。
                if ("FEDERATED_SQL".equals(version.getSqlType())) {
                    FederatedQueryExecutor.FederatedQueryResult result =
                            federatedQueryExecutor.execute(service, version, boundParams.params());
                    queryResultGuardService.validate(service, result.rows(), result.diagnosticSummary(), "FEDERATED_EXECUTION");
                    Map<String, Object> diagnosticSummary = enrichDiagnosticSummary(
                            result.diagnosticSummary(),
                            cacheKeySnapshot,
                            cacheHit.missReason(),
                            null,
                            false
                    );
                    queryResultCache.put(cacheKeySnapshot.cacheKey(), cacheKeySnapshot.ttlSeconds(), result.rows(), diagnosticSummary);
                    return queryResponseAssembler.successItem(index, result.rows(), result.elapsedMs(), false, diagnosticSummary);
                } else {
                    SingleSourceQueryExecutor.SingleSourceQueryResult result =
                            singleSourceQueryExecutor.execute(service, version, boundParams.params());
                    queryResultGuardService.validate(service, result.rows(), result.diagnosticSummary(), "SINGLE_SOURCE_EXECUTION");
                    Map<String, Object> diagnosticSummary = enrichDiagnosticSummary(
                            result.diagnosticSummary(),
                            cacheKeySnapshot,
                            cacheHit.missReason(),
                            null,
                            false
                    );
                    queryResultCache.put(cacheKeySnapshot.cacheKey(), cacheKeySnapshot.ttlSeconds(), result.rows(), diagnosticSummary);
                    return queryResponseAssembler.successItem(index, result.rows(), result.elapsedMs(), false, diagnosticSummary);
                }
            }

            // 缓存策略关闭时仍要保留统一的诊断字段，方便前端和审计区分未命中与未启用。
            if ("FEDERATED_SQL".equals(version.getSqlType())) {
                FederatedQueryExecutor.FederatedQueryResult result =
                        federatedQueryExecutor.execute(service, version, boundParams.params());
                queryResultGuardService.validate(service, result.rows(), result.diagnosticSummary(), "FEDERATED_EXECUTION");
                return queryResponseAssembler.successItem(
                        index,
                        result.rows(),
                        result.elapsedMs(),
                        false,
                        enrichDiagnosticSummary(result.diagnosticSummary(), cacheKeySnapshot, null, "POLICY_DISABLED", false)
                );
            } else {
                SingleSourceQueryExecutor.SingleSourceQueryResult result =
                        singleSourceQueryExecutor.execute(service, version, boundParams.params());
                queryResultGuardService.validate(service, result.rows(), result.diagnosticSummary(), "SINGLE_SOURCE_EXECUTION");
                return queryResponseAssembler.successItem(
                        index,
                        result.rows(),
                        result.elapsedMs(),
                        false,
                        enrichDiagnosticSummary(result.diagnosticSummary(), cacheKeySnapshot, null, "POLICY_DISABLED", false)
                );
            }
        } catch (QueryParamValidationException ex) {
            return queryResponseAssembler.failureItemFromParamValidation(index, ex);
        } catch (FederatedQueryExecutionException ex) {
            return queryResponseAssembler.failureItemFromFederatedExecution(index, ex);
        } catch (DataServiceException ex) {
            return queryResponseAssembler.failureItemFromBusinessException(index, ex);
        }
    }

    private Map<String, Object> enrichDiagnosticSummary(Map<String, Object> originalSummary,
                                                        QueryCacheKeyService.QueryCacheKeySnapshot cacheKeySnapshot,
                                                        String cacheMissReason,
                                                        String cacheWriteSkippedReason,
                                                        boolean cacheHit) {
        Map<String, Object> diagnosticSummary = new LinkedHashMap<>(originalSummary);
        diagnosticSummary.put("cacheKeyTemplate", cacheKeySnapshot.cacheKeyTemplate());
        diagnosticSummary.put("cacheNamespaceVersion", cacheKeySnapshot.cacheNamespaceVersion());
        diagnosticSummary.put("paramHash", cacheKeySnapshot.paramHash());
        diagnosticSummary.put("cacheHit", cacheHit);
        diagnosticSummary.put("cacheMissReason", cacheHit ? null : (cacheMissReason == null ? cacheKeySnapshot.cacheMissReason() : cacheMissReason));
        if (cacheKeySnapshot.contextDigest() != null) {
            diagnosticSummary.put("contextDigest", cacheKeySnapshot.contextDigest());
        }
        if (cacheKeySnapshot.cacheIsolationReason() != null) {
            diagnosticSummary.put("cacheIsolationReason", cacheKeySnapshot.cacheIsolationReason());
        }
        if (cacheWriteSkippedReason != null) {
            diagnosticSummary.put("cacheWriteSkippedReason", cacheWriteSkippedReason);
        }
        diagnosticSummary.put("cacheKey", cacheKeySnapshot.cacheKey());
        return diagnosticSummary;
    }

    private DsServiceRecord requirePublishedService(String serviceCode) {
        DsServiceRecord service = serviceRepository.findByServiceCode(serviceCode);
        if (service == null) {
            throw new ResourceNotFoundException("服务不存在: " + serviceCode);
        }
        if (!"PUBLISHED".equals(service.getStatus()) || service.getCurrentVersion() == null) {
            throw new ResourceConflictException(ErrorCode.RESOURCE_CONFLICT, "服务未发布: " + serviceCode);
        }
        return service;
    }

    private DsServiceVersionRecord requirePublishedVersion(DsServiceRecord service) {
        DsServiceVersionRecord version =
                serviceVersionRepository.findByServiceIdAndVersion(service.getId(), service.getCurrentVersion());
        if (version == null || !"PUBLISHED".equals(version.getStatus())) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "已发布服务缺少当前版本快照: " + service.getServiceCode());
        }
        return version;
    }

    private void recordQueryAudit(DsServiceRecord service,
                                  QueryRequest request,
                                  QueryRequestContextService.NormalizedQueryRequestContext requestContext,
                                  QueryResponse response,
                                  RuntimeException error,
                                  long elapsedMs,
                                  String traceId) {
        try {
            auditService.recordQueryExecution(
                    service == null ? null : service.getId(),
                    service == null ? request.serviceCode() : service.getServiceCode(),
                    service == null ? null : service.getCurrentVersion(),
                    request,
                    requestContext,
                    response,
                    error,
                    elapsedMs,
                    traceId
            );
        } catch (RuntimeException auditEx) {
            // 主链路已有业务异常时，附加审计异常即可，不能让审计覆盖掉原始失败原因。
            if (error != null) {
                error.addSuppressed(auditEx);
                return;
            }
            throw auditEx;
        }
    }
}
