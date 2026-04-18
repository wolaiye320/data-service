package cn.dtkeys.dataservice.query.cache;

public interface QueryCache {

    CacheLookupResult get(String key);

    void put(String key, QueryCacheValue value, long ttlSeconds);

    void evict(String key);

    void evictByPrefix(String keyPrefix);
}
