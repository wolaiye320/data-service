package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.domain.DsCachePolicyRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsCachePolicyRepository;
import cn.dtkeys.dataservice.core.meta.repository.DsServiceRepository;
import cn.dtkeys.dataservice.core.meta.web.request.CachePolicyUpsertRequest;
import cn.dtkeys.dataservice.core.meta.web.response.CachePolicyResponse;
import cn.dtkeys.dataservice.core.meta.web.support.OperatorContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CachePolicyService {

    private static final Set<String> SUPPORTED_CONTEXT_KEYS = Set.of("tenantId", "callerId", "traceId");

    private final DsServiceRepository serviceRepository;
    private final DsCachePolicyRepository cachePolicyRepository;
    private final QueryCachePolicyLoader queryCachePolicyLoader;
    private final QueryResultCache queryResultCache;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CachePolicyService(DsServiceRepository serviceRepository,
                              DsCachePolicyRepository cachePolicyRepository,
                              QueryCachePolicyLoader queryCachePolicyLoader,
                              QueryResultCache queryResultCache,
                              AuditService auditService,
                              ObjectMapper objectMapper) {
        this.serviceRepository = serviceRepository;
        this.cachePolicyRepository = cachePolicyRepository;
        this.queryCachePolicyLoader = queryCachePolicyLoader;
        this.queryResultCache = queryResultCache;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public CachePolicyResponse detail(Long serviceId) {
        DsServiceRecord service = requireService(serviceId);
        return toResponse(serviceId, queryCachePolicyLoader.load(service));
    }

    @Transactional
    public CachePolicyResponse upsert(Long serviceId,
                                      CachePolicyUpsertRequest request,
                                      OperatorContext operatorContext,
                                      String traceId) {
        DsServiceRecord service = requireService(serviceId);
        validateRequest(request);
        DsCachePolicyRecord existing = cachePolicyRepository.findByServiceId(serviceId);
        List<String> contextKeys = normalizeContextKeys(request.contextKeys());
        String contextKeysJson = writeContextKeys(contextKeys);
        if (existing == null) {
            DsCachePolicyRecord record = new DsCachePolicyRecord();
            record.setServiceId(serviceId);
            record.setEnabled(request.enabled());
            record.setTtlSeconds(request.ttlSeconds());
            record.setCacheKeyTemplate(request.cacheKeyTemplate());
            record.setMaxEntries(request.maxEntries());
            record.setContextKeysJson(contextKeysJson);
            record.setRemark(request.remark());
            record.setDeleted(false);
            record.setCreatedBy(operatorContext.operator());
            record.setUpdatedBy(operatorContext.operator());
            cachePolicyRepository.insert(record);
        } else {
            cachePolicyRepository.updatePolicy(
                    serviceId,
                    request.enabled(),
                    request.ttlSeconds(),
                    request.cacheKeyTemplate(),
                    request.maxEntries(),
                    contextKeysJson,
                    request.remark(),
                    operatorContext.operator()
            );
        }
        QueryCachePolicyLoader.QueryCachePolicySnapshot snapshot = queryCachePolicyLoader.load(service);
        auditService.recordServiceEvent(
                serviceId,
                "UPSERT_CACHE_POLICY",
                service.getServiceCode(),
                "SUCCESS",
                "更新缓存策略成功",
                buildAuditDetail(snapshot),
                operatorContext,
                traceId
        );
        return toResponse(serviceId, snapshot);
    }

    @Transactional
    public CacheEvictResponse clearCache(Long serviceId,
                                         OperatorContext operatorContext,
                                         String traceId) {
        DsServiceRecord service = requireService(serviceId);
        int evictedEntries = queryResultCache.evictByPrefix(cacheKeyPrefix(service), "EXPLICIT_EVICT");
        auditService.recordServiceEvent(
                serviceId,
                "CLEAR_CACHE_POLICY_CACHE",
                service.getServiceCode(),
                "SUCCESS",
                "显式清理服务缓存成功",
                buildEvictAuditDetail(service.getServiceCode(), evictedEntries),
                operatorContext,
                traceId
        );
        return new CacheEvictResponse(serviceId, service.getServiceCode(), evictedEntries, "EXPLICIT_EVICT");
    }

    private void validateRequest(CachePolicyUpsertRequest request) {
        if (Boolean.TRUE.equals(request.enabled()) && request.ttlSeconds() == null) {
            throw new cn.dtkeys.dataservice.core.error.DataServiceException(
                    cn.dtkeys.dataservice.core.error.ErrorCode.INVALID_ARGUMENT,
                    "启用缓存时必须指定 ttlSeconds"
            );
        }
        List<String> contextKeys = normalizeContextKeys(request.contextKeys());
        String unsupportedKey = contextKeys.stream()
                .filter(key -> !SUPPORTED_CONTEXT_KEYS.contains(key))
                .findFirst()
                .orElse(null);
        if (unsupportedKey != null) {
            throw new cn.dtkeys.dataservice.core.error.DataServiceException(
                    cn.dtkeys.dataservice.core.error.ErrorCode.INVALID_ARGUMENT,
                    "缓存策略 contextKeys 仅支持 tenantId、callerId、traceId: " + unsupportedKey
            );
        }
        if ((request.cacheKeyTemplate() == null || request.cacheKeyTemplate().isBlank())
                && !contextKeys.isEmpty()) {
            return;
        }
        if ((request.cacheKeyTemplate() != null && request.cacheKeyTemplate().contains("{contextDigest}"))
                && contextKeys.isEmpty()) {
            throw new cn.dtkeys.dataservice.core.error.DataServiceException(
                    cn.dtkeys.dataservice.core.error.ErrorCode.INVALID_ARGUMENT,
                    "使用 {contextDigest} 模板占位符时必须配置 contextKeys"
            );
        }
    }

    private DsServiceRecord requireService(Long serviceId) {
        DsServiceRecord service = serviceRepository.findById(serviceId);
        if (service == null) {
            throw new cn.dtkeys.dataservice.core.error.ResourceNotFoundException("服务不存在: " + serviceId);
        }
        return service;
    }

    private List<String> normalizeContextKeys(List<String> contextKeys) {
        if (contextKeys == null || contextKeys.isEmpty()) {
            return List.of();
        }
        return contextKeys.stream()
                .map(this::normalizeBlank)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        set -> set.stream().toList()
                ));
    }

    private String writeContextKeys(List<String> contextKeys) {
        try {
            return objectMapper.writeValueAsString(contextKeys == null ? List.of() : contextKeys);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("缓存策略上下文字段序列化失败", ex);
        }
    }

    private CachePolicyResponse toResponse(Long serviceId,
                                           QueryCachePolicyLoader.QueryCachePolicySnapshot snapshot) {
        DsCachePolicyRecord record = cachePolicyRepository.findByServiceId(serviceId);
        return new CachePolicyResponse(
                serviceId,
                snapshot.enabled(),
                snapshot.ttlSeconds(),
                snapshot.cacheKeyTemplate(),
                snapshot.maxEntries(),
                snapshot.contextKeys(),
                snapshot.remark(),
                record == null ? null : record.getCreatedAt(),
                record == null ? null : record.getCreatedBy(),
                record == null ? null : record.getUpdatedAt(),
                record == null ? null : record.getUpdatedBy()
        );
    }

    private String buildAuditDetail(QueryCachePolicyLoader.QueryCachePolicySnapshot snapshot) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("enabled", snapshot.enabled());
        detail.put("ttlSeconds", snapshot.ttlSeconds());
        detail.put("cacheKeyTemplate", snapshot.cacheKeyTemplate());
        detail.put("maxEntries", snapshot.maxEntries());
        detail.put("contextKeys", snapshot.contextKeys());
        detail.put("remark", snapshot.remark());
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("缓存策略审计详情序列化失败", ex);
        }
    }

    private String buildEvictAuditDetail(String serviceCode, int evictedEntries) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("serviceCode", serviceCode);
        detail.put("evictedEntries", evictedEntries);
        detail.put("missReason", "EXPLICIT_EVICT");
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("缓存清理审计详情序列化失败", ex);
        }
    }

    private String cacheKeyPrefix(DsServiceRecord service) {
        return "data-service:" + service.getServiceCode() + ":";
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record CacheEvictResponse(
            Long serviceId,
            String serviceCode,
            int evictedEntries,
            String missReason
    ) {
    }
}
