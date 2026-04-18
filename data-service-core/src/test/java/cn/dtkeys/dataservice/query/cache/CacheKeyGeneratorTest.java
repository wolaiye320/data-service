package cn.dtkeys.dataservice.query.cache;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyGeneratorTest {

    private final CacheKeyGenerator cacheKeyGenerator = new CacheKeyGenerator();

    @Test
    void shouldGenerateStableKeyForSameLogicalParams() {
        Map<String, Object> firstParams = new LinkedHashMap<>();
        firstParams.put("customerId", 1001L);
        firstParams.put("active", true);

        Map<String, Object> secondParams = new LinkedHashMap<>();
        secondParams.put("active", true);
        secondParams.put("customerId", 1001L);

        String firstKey = cacheKeyGenerator.generate("customer_order_query", 3, firstParams);
        String secondKey = cacheKeyGenerator.generate("customer_order_query", 3, secondParams);

        assertThat(firstKey).isEqualTo(secondKey);
        assertThat(firstKey).startsWith("data-service:customer_order_query:v3:");
    }

    @Test
    void shouldDifferentiateServiceVersionAndParams() {
        String baseKey = cacheKeyGenerator.generate("customer_order_query", 1, Map.of("customerId", 1001L));
        String changedVersionKey = cacheKeyGenerator.generate("customer_order_query", 2, Map.of("customerId", 1001L));
        String changedParamKey = cacheKeyGenerator.generate("customer_order_query", 1, Map.of("customerId", 1002L));

        assertThat(baseKey).isNotEqualTo(changedVersionKey);
        assertThat(baseKey).isNotEqualTo(changedParamKey);
    }
}
