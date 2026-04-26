package cn.dtkeys.dataservice.core.meta.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QueryResultCacheTest {

    @Test
    void shouldReturnHitBeforeTtlAndExpireAfterTtl() throws Exception {
        QueryResultCache cache = new QueryResultCache();
        cache.put("cache-key", 1, List.of(Map.of("id", 1)), Map.of("cacheHit", false));

        QueryResultCache.CacheHit first = cache.get("cache-key", 1);
        assertThat(first.hit()).isTrue();
        assertThat(first.rows()).hasSize(1);

        Thread.sleep(1100L);

        QueryResultCache.CacheHit second = cache.get("cache-key", 1);
        assertThat(second.hit()).isFalse();
        assertThat(second.missReason()).isEqualTo("TTL_EXPIRED");
    }

    @Test
    void shouldReturnExplicitEvictMissReasonAfterEviction() {
        QueryResultCache cache = new QueryResultCache();
        cache.put("data-service:svc_query:v1:key-a", 300, List.of(Map.of("id", 1)), Map.of("cacheHit", false));
        cache.put("data-service:svc_query:v1:key-b", 300, List.of(Map.of("id", 2)), Map.of("cacheHit", false));

        int evicted = cache.evictByPrefix("data-service:svc_query:", "EXPLICIT_EVICT");

        assertThat(evicted).isEqualTo(2);
        assertThat(cache.get("data-service:svc_query:v1:key-a", 300).missReason()).isEqualTo("EXPLICIT_EVICT");
        assertThat(cache.get("data-service:svc_query:v1:key-a", 300).missReason()).isEqualTo("MISS");
    }

    @Test
    void shouldClearAllEntriesAndMissReasons() {
        QueryResultCache cache = new QueryResultCache();
        cache.put("data-service:svc_query:v1:key-a", 300, List.of(Map.of("id", 1)), Map.of("cacheHit", false));
        cache.evictByPrefix("data-service:svc_query:", "EXPLICIT_EVICT");

        cache.clearAll();

        QueryResultCache.CacheHit cacheHit = cache.get("data-service:svc_query:v1:key-a", 300);
        assertThat(cacheHit.hit()).isFalse();
        assertThat(cacheHit.missReason()).isEqualTo("MISS");
    }
}
