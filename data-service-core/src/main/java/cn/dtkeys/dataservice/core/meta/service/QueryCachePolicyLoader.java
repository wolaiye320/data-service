package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.domain.DsCachePolicyRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsCachePolicyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 读取并规范化服务级缓存策略快照。
 */
@Service
public class QueryCachePolicyLoader {

    private static final String DEFAULT_CACHE_KEY_TEMPLATE = "data-service:{serviceCode}:v{version}:{paramHash}";

    private final DsCachePolicyRepository cachePolicyRepository;
    private final ObjectMapper objectMapper;

    public QueryCachePolicyLoader(DsCachePolicyRepository cachePolicyRepository, ObjectMapper objectMapper) {
        this.cachePolicyRepository = cachePolicyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 加载指定服务的缓存策略；不存在时返回禁用态默认快照。
     */
    public QueryCachePolicySnapshot load(DsServiceRecord service) {
        DsCachePolicyRecord record = cachePolicyRepository.findByServiceId(service.getId());
        if (record == null) {
            return QueryCachePolicySnapshot.disabled(service.getId(), DEFAULT_CACHE_KEY_TEMPLATE);
        }
        List<String> contextKeys = readContextKeys(record.getContextKeysJson());
        return new QueryCachePolicySnapshot(
                service.getId(),
                Boolean.TRUE.equals(record.getEnabled()),
                record.getTtlSeconds(),
                normalizeTemplate(record.getCacheKeyTemplate()),
                record.getMaxEntries(),
                contextKeys,
                record.getRemark()
        );
    }

    private String normalizeTemplate(String cacheKeyTemplate) {
        if (cacheKeyTemplate == null || cacheKeyTemplate.isBlank()) {
            return DEFAULT_CACHE_KEY_TEMPLATE;
        }
        return cacheKeyTemplate.trim();
    }

    private List<String> readContextKeys(String contextKeysJson) {
        if (contextKeysJson == null || contextKeysJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(contextKeysJson, new TypeReference<List<String>>() {
            });
            return parsed.stream()
                    .map(this::normalizeBlank)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                            set -> set.stream().toList()
                    ));
        } catch (Exception ex) {
            throw new IllegalStateException("缓存策略上下文字段解析失败", ex);
        }
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record QueryCachePolicySnapshot(
            Long serviceId,
            boolean enabled,
            Integer ttlSeconds,
            String cacheKeyTemplate,
            Integer maxEntries,
            List<String> contextKeys,
            String remark
    ) {

        public static QueryCachePolicySnapshot disabled(Long serviceId, String cacheKeyTemplate) {
            return new QueryCachePolicySnapshot(serviceId, false, null, cacheKeyTemplate, null, List.of(), null);
        }
    }
}
