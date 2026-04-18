package cn.dtkeys.dataservice.query.cache;

public record CacheLookupResult(boolean hit, QueryCacheValue value) {

    public static CacheLookupResult hit(QueryCacheValue value) {
        return new CacheLookupResult(true, value);
    }

    public static CacheLookupResult miss() {
        return new CacheLookupResult(false, null);
    }
}
