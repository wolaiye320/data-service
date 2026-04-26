package cn.dtkeys.dataservice.core.meta.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QueryResultCache {

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> nextMissReasons = new ConcurrentHashMap<>();

    public CacheHit get(String cacheKey, Integer ttlSeconds) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return CacheHit.miss("MISS");
        }
        CacheEntry entry = cache.get(cacheKey);
        if (entry == null) {
            return CacheHit.miss(consumeMissReason(cacheKey));
        }
        long now = System.currentTimeMillis();
        if (ttlSeconds != null && ttlSeconds > 0 && now - entry.createdAtMillis() >= ttlSeconds * 1000L) {
            cache.remove(cacheKey);
            return CacheHit.miss("TTL_EXPIRED");
        }
        return CacheHit.hit(entry.rows(), entry.diagnosticSummary());
    }

    public void put(String cacheKey,
                    Integer ttlSeconds,
                    List<Map<String, Object>> rows,
                    Map<String, Object> diagnosticSummary) {
        if (cacheKey == null || cacheKey.isBlank() || ttlSeconds == null || ttlSeconds <= 0) {
            return;
        }
        cache.put(cacheKey, new CacheEntry(
                System.currentTimeMillis(),
                List.copyOf(rows),
                Map.copyOf(diagnosticSummary)
        ));
        nextMissReasons.remove(cacheKey);
    }

    public int evictByPrefix(String cacheKeyPrefix, String missReason) {
        if (cacheKeyPrefix == null || cacheKeyPrefix.isBlank()) {
            return 0;
        }
        int removed = 0;
        for (String cacheKey : cache.keySet()) {
            if (!cacheKey.startsWith(cacheKeyPrefix)) {
                continue;
            }
            CacheEntry removedEntry = cache.remove(cacheKey);
            if (removedEntry != null) {
                removed++;
                nextMissReasons.put(cacheKey, missReason == null || missReason.isBlank() ? "EXPLICIT_EVICT" : missReason);
            }
        }
        return removed;
    }

    public void clearAll() {
        cache.clear();
        nextMissReasons.clear();
    }

    private String consumeMissReason(String cacheKey) {
        String missReason = nextMissReasons.remove(cacheKey);
        return missReason == null || missReason.isBlank() ? "MISS" : missReason;
    }

    public record CacheEntry(
            long createdAtMillis,
            List<Map<String, Object>> rows,
            Map<String, Object> diagnosticSummary
    ) {
    }

    public record CacheHit(
            boolean hit,
            String missReason,
            List<Map<String, Object>> rows,
            Map<String, Object> diagnosticSummary
    ) {

        public static CacheHit hit(List<Map<String, Object>> rows, Map<String, Object> diagnosticSummary) {
            return new CacheHit(true, null, rows, diagnosticSummary);
        }

        public static CacheHit miss(String missReason) {
            return new CacheHit(false, missReason, List.of(), Map.of());
        }
    }
}
