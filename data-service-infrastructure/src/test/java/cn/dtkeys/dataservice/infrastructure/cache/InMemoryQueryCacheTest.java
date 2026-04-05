package cn.dtkeys.dataservice.infrastructure.cache;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryQueryCacheTest {

    private final CacheMetricsCollector cacheMetricsCollector = new CacheMetricsCollector();
    private final InMemoryQueryCache queryCache = new InMemoryQueryCache(cacheMetricsCollector);

    @Test
    void shouldReadBackCachedValueBeforeTtlExpires() {
        QueryCacheValue cacheValue = new QueryCacheValue(
            List.of(Map.of("customerId", 1001L)),
            Map.of("serviceCode", "customer_order_query", "cacheHit", false)
        );

        queryCache.put("k1", cacheValue, 60);

        CacheLookupResult lookupResult = queryCache.get("k1");

        assertThat(lookupResult.hit()).isTrue();
        assertThat(lookupResult.value()).isEqualTo(cacheValue);
    }

    @Test
    void shouldExpireValueAfterTtl() throws InterruptedException {
        queryCache.put("k2", new QueryCacheValue(List.of(), Map.of()), 1);

        Thread.sleep(1100L);

        CacheLookupResult lookupResult = queryCache.get("k2");
        assertThat(lookupResult.hit()).isFalse();
        assertThat(lookupResult.value()).isNull();
    }

    @Test
    void shouldEvictValuesByPrefix() {
        queryCache.put("data-service:svc:v1:a", new QueryCacheValue(List.of(), Map.of()), 60);
        queryCache.put("data-service:svc:v1:b", new QueryCacheValue(List.of(), Map.of()), 60);
        queryCache.put("data-service:other:v1:c", new QueryCacheValue(List.of(), Map.of()), 60);

        queryCache.evictByPrefix("data-service:svc:v1:");

        assertThat(queryCache.get("data-service:svc:v1:a").hit()).isFalse();
        assertThat(queryCache.get("data-service:svc:v1:b").hit()).isFalse();
        assertThat(queryCache.get("data-service:other:v1:c").hit()).isTrue();
    }

    @Test
    void shouldCollectCacheMetrics() {
        queryCache.put("k3", new QueryCacheValue(List.of(), Map.of()), 60);
        queryCache.get("k3");
        queryCache.get("missing");
        queryCache.evict("k3");

        assertThat(cacheMetricsCollector.toSummary())
            .containsEntry("hitCount", 1L)
            .containsEntry("missCount", 1L)
            .containsEntry("putCount", 1L)
            .containsEntry("evictCount", 1L)
            .containsEntry("errorCount", 0L);
    }
}
