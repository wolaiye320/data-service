package cn.dtkeys.dataservice.infrastructure.cache;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryQueryCache implements QueryCache {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final CacheMetricsCollector cacheMetricsCollector;

    public InMemoryQueryCache(CacheMetricsCollector cacheMetricsCollector) {
        this.cacheMetricsCollector = cacheMetricsCollector;
    }

    @Override
    public CacheLookupResult get(String key) {
        try {
            CacheEntry cacheEntry = cache.get(key);
            if (cacheEntry == null) {
                cacheMetricsCollector.recordMiss();
                return CacheLookupResult.miss();
            }
            if (cacheEntry.expiredAtEpochMilli() <= System.currentTimeMillis()) {
                cache.remove(key);
                cacheMetricsCollector.recordExpired();
                cacheMetricsCollector.recordMiss();
                return CacheLookupResult.miss();
            }
            cacheMetricsCollector.recordHit();
            return CacheLookupResult.hit(cacheEntry.value());
        } catch (RuntimeException exception) {
            cacheMetricsCollector.recordError();
            throw exception;
        }
    }

    @Override
    public void put(String key, QueryCacheValue value, long ttlSeconds) {
        try {
            long expiredAtEpochMilli = Instant.now().plusSeconds(ttlSeconds).toEpochMilli();
            cache.put(key, new CacheEntry(value, expiredAtEpochMilli));
            cacheMetricsCollector.recordPut();
        } catch (RuntimeException exception) {
            cacheMetricsCollector.recordError();
            throw exception;
        }
    }

    @Override
    public void evict(String key) {
        try {
            CacheEntry removed = cache.remove(key);
            if (removed != null) {
                cacheMetricsCollector.recordEvict(1);
            }
        } catch (RuntimeException exception) {
            cacheMetricsCollector.recordError();
            throw exception;
        }
    }

    @Override
    public void evictByPrefix(String keyPrefix) {
        try {
            long removedCount = 0;
            Iterator<String> iterator = cache.keySet().iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (key.startsWith(keyPrefix)) {
                    iterator.remove();
                    removedCount++;
                }
            }
            cacheMetricsCollector.recordEvict(removedCount);
        } catch (RuntimeException exception) {
            cacheMetricsCollector.recordError();
            throw exception;
        }
    }

    private record CacheEntry(QueryCacheValue value, long expiredAtEpochMilli) {
    }
}
