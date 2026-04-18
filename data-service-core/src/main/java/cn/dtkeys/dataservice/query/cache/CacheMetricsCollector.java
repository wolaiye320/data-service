package cn.dtkeys.dataservice.query.cache;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CacheMetricsCollector {

    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong putCount = new AtomicLong();
    private final AtomicLong evictCount = new AtomicLong();
    private final AtomicLong expiredCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();

    public void recordHit() {
        hitCount.incrementAndGet();
    }

    public void recordMiss() {
        missCount.incrementAndGet();
    }

    public void recordPut() {
        putCount.incrementAndGet();
    }

    public void recordEvict(long count) {
        evictCount.addAndGet(Math.max(count, 0));
    }

    public void recordExpired() {
        expiredCount.incrementAndGet();
    }

    public void recordError() {
        errorCount.incrementAndGet();
    }

    public Map<String, Object> toSummary() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long totalLookups = hits + misses;
        double hitRate = totalLookups == 0 ? 0D : (double) hits / totalLookups;

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("hitCount", hits);
        summary.put("missCount", misses);
        summary.put("putCount", putCount.get());
        summary.put("evictCount", evictCount.get());
        summary.put("expiredCount", expiredCount.get());
        summary.put("errorCount", errorCount.get());
        summary.put("lookupCount", totalLookups);
        summary.put("hitRate", hitRate);
        return summary;
    }
}
