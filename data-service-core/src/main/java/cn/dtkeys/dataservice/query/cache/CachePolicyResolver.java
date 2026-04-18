package cn.dtkeys.dataservice.query.cache;

import cn.dtkeys.dataservice.service.model.DSCachePolicy;
import org.springframework.stereotype.Component;

@Component
public class CachePolicyResolver {

    public ResolvedCachePolicy resolve(String serviceCode, int version, DSCachePolicy cachePolicy) {
        if (cachePolicy == null || !Boolean.TRUE.equals(cachePolicy.getEnabled())) {
            return ResolvedCachePolicy.disabled(serviceCode, version);
        }

        int ttlSeconds = cachePolicy.getTtlSeconds() == null || cachePolicy.getTtlSeconds() <= 0
            ? 300
            : cachePolicy.getTtlSeconds();
        String keyTemplate = cachePolicy.getCacheKeyTemplate() == null || cachePolicy.getCacheKeyTemplate().isBlank()
            ? "data-service:{serviceCode}:v{version}:{paramHash}"
            : cachePolicy.getCacheKeyTemplate();
        return new ResolvedCachePolicy(
            serviceCode,
            version,
            true,
            ttlSeconds,
            keyTemplate,
            ResolvedCachePolicy.buildPrefix(serviceCode, version)
        );
    }
}
