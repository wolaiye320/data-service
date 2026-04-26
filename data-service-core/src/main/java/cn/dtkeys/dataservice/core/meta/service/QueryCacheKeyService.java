package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryCacheKeyService {

    private final QueryCachePolicyLoader queryCachePolicyLoader;
    private final ObjectMapper objectMapper;

    public QueryCacheKeyService(QueryCachePolicyLoader queryCachePolicyLoader, ObjectMapper objectMapper) {
        this.queryCachePolicyLoader = queryCachePolicyLoader;
        this.objectMapper = objectMapper;
    }

    public QueryCacheKeySnapshot build(DsServiceRecord service,
                                       DsServiceVersionRecord version,
                                       Map<String, Object> params,
                                       QueryRequestContextService.NormalizedQueryRequestContext requestContext) {
        QueryCachePolicyLoader.QueryCachePolicySnapshot policy = queryCachePolicyLoader.load(service);
        String paramHash = sha256Hex(writeCanonicalJson(canonicalizeParams(params)));
        String contextDigest = buildContextDigest(policy.contextKeys(), requestContext);
        String cacheKey = renderKey(policy.cacheKeyTemplate(), service.getServiceCode(), version.getVersion(), paramHash, contextDigest);
        String missReason = policy.enabled() ? "MISS" : "POLICY_DISABLED";
        String isolationReason = policy.contextKeys().isEmpty() ? null : buildIsolationReason(policy.contextKeys(), requestContext);
        return new QueryCacheKeySnapshot(
                policy.enabled(),
                policy.ttlSeconds(),
                policy.cacheKeyTemplate(),
                version.getVersion(),
                paramHash,
                contextDigest,
                cacheKey,
                missReason,
                isolationReason
        );
    }

    private Map<String, Object> canonicalizeParams(Map<String, Object> params) {
        Map<String, Object> canonical = new java.util.TreeMap<>();
        if (params == null) {
            return canonical;
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            canonical.put(entry.getKey(), canonicalizeValue(entry.getValue()));
        }
        return canonical;
    }

    private Object canonicalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new java.util.TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), canonicalizeValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalizeValue).toList();
        }
        return value;
    }

    private String buildContextDigest(List<String> contextKeys,
                                      QueryRequestContextService.NormalizedQueryRequestContext requestContext) {
        if (contextKeys == null || contextKeys.isEmpty()) {
            return null;
        }
        Map<String, Object> digestInput = new LinkedHashMap<>();
        for (String contextKey : contextKeys) {
            digestInput.put(contextKey, switch (contextKey) {
                case "tenantId" -> requestContext.tenantId();
                case "callerId" -> requestContext.callerId();
                case "traceId" -> requestContext.traceId();
                default -> null;
            });
        }
        return sha256Hex(writeCanonicalJson(digestInput));
    }

    private String buildIsolationReason(List<String> contextKeys,
                                        QueryRequestContextService.NormalizedQueryRequestContext requestContext) {
        List<String> effectiveKeys = contextKeys.stream()
                .filter(key -> switch (key) {
                    case "tenantId" -> requestContext.tenantId() != null;
                    case "callerId" -> requestContext.callerId() != null;
                    case "traceId" -> requestContext.traceId() != null;
                    default -> false;
                })
                .toList();
        if (effectiveKeys.isEmpty()) {
            return "CONTEXT_KEYS_DECLARED";
        }
        return "CONTEXT_KEYS_DECLARED:" + String.join(",", effectiveKeys);
    }

    private String renderKey(String template,
                             String serviceCode,
                             Integer version,
                             String paramHash,
                             String contextDigest) {
        String rendered = template
                .replace("{serviceCode}", serviceCode)
                .replace("{version}", String.valueOf(version))
                .replace("{paramHash}", paramHash);
        if (contextDigest == null || contextDigest.isBlank()) {
            return rendered.replace(":{contextDigest}", "").replace("{contextDigest}", "");
        }
        return rendered.replace("{contextDigest}", contextDigest);
    }

    private String writeCanonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("缓存键标准化序列化失败", ex);
        }
    }

    private String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前环境不支持 SHA-256", ex);
        }
    }

    public record QueryCacheKeySnapshot(
            boolean cacheEnabled,
            Integer ttlSeconds,
            String cacheKeyTemplate,
            Integer cacheNamespaceVersion,
            String paramHash,
            String contextDigest,
            String cacheKey,
            String cacheMissReason,
            String cacheIsolationReason
    ) {
    }
}
