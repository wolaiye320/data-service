package cn.dtkeys.dataservice.query.cache;

public record ResolvedCachePolicy(String serviceCode,
                                  int version,
                                  boolean enabled,
                                  int ttlSeconds,
                                  String keyTemplate,
                                  String keyPrefix) {

    public static ResolvedCachePolicy disabled(String serviceCode, int version) {
        return new ResolvedCachePolicy(serviceCode, version, false, 0, "", buildPrefix(serviceCode, version));
    }

    public static String buildPrefix(String serviceCode, int version) {
        return "data-service:" + serviceCode + ":v" + version + ":";
    }
}
