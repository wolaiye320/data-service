package cn.dtkeys.dataservice.infrastructure.cache;

import cn.dtkeys.dataservice.domain.model.DSCachePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CachePolicyResolverTest {

    private final CachePolicyResolver cachePolicyResolver = new CachePolicyResolver();

    @Test
    void shouldReturnDisabledWhenPolicyMissing() {
        ResolvedCachePolicy policy = cachePolicyResolver.resolve("customer_order_query", 1, null);

        assertThat(policy.enabled()).isFalse();
        assertThat(policy.serviceCode()).isEqualTo("customer_order_query");
        assertThat(policy.version()).isEqualTo(1);
    }

    @Test
    void shouldResolveEnabledPolicyWithDefaults() {
        DSCachePolicy cachePolicy = new DSCachePolicy();
        cachePolicy.setEnabled(true);
        cachePolicy.setTtlSeconds(120);
        cachePolicy.setCacheKeyTemplate("data-service:{serviceCode}:v{version}:{paramHash}");

        ResolvedCachePolicy policy = cachePolicyResolver.resolve("customer_order_query", 2, cachePolicy);

        assertThat(policy.enabled()).isTrue();
        assertThat(policy.ttlSeconds()).isEqualTo(120);
        assertThat(policy.keyPrefix()).isEqualTo("data-service:customer_order_query:v2:");
    }
}
