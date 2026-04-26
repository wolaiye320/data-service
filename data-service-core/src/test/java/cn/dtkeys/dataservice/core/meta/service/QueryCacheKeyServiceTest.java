package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.domain.DsCachePolicyRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsCachePolicyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryCacheKeyServiceTest {

    private final DsCachePolicyRepository cachePolicyRepository = mock(DsCachePolicyRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QueryCachePolicyLoader loader = new QueryCachePolicyLoader(cachePolicyRepository, objectMapper);
    private final QueryCacheKeyService service = new QueryCacheKeyService(loader, objectMapper);

    @Test
    void shouldBuildStableHashesAndContextDigest() {
        DsServiceRecord dsService = new DsServiceRecord();
        dsService.setId(1L);
        dsService.setServiceCode("svc_query");
        DsServiceVersionRecord version = new DsServiceVersionRecord();
        version.setVersion(3);

        DsCachePolicyRecord policy = new DsCachePolicyRecord();
        policy.setServiceId(1L);
        policy.setEnabled(true);
        policy.setTtlSeconds(300);
        policy.setCacheKeyTemplate("data-service:{serviceCode}:v{version}:{paramHash}:{contextDigest}");
        policy.setContextKeysJson("[\"tenantId\",\"callerId\"]");
        when(cachePolicyRepository.findByServiceId(1L)).thenReturn(policy);

        QueryRequestContextService.NormalizedQueryRequestContext requestContext =
                new QueryRequestContextService.NormalizedQueryRequestContext(
                        "tenant-a",
                        "caller-a",
                        "trace-a",
                        java.util.List.of("callerId"),
                        java.util.List.of("tenantId", "callerId", "traceId")
                );

        QueryCacheKeyService.QueryCacheKeySnapshot snapshot = service.build(
                dsService,
                version,
                java.util.Map.of("b", 2, "a", 1),
                requestContext
        );

        assertThat(snapshot.cacheEnabled()).isTrue();
        assertThat(snapshot.cacheNamespaceVersion()).isEqualTo(3);
        assertThat(snapshot.paramHash()).hasSize(64);
        assertThat(snapshot.contextDigest()).hasSize(64);
        assertThat(snapshot.cacheKey()).startsWith("data-service:svc_query:v3:");
        assertThat(snapshot.cacheMissReason()).isEqualTo("MISS");
        assertThat(snapshot.cacheIsolationReason()).isEqualTo("CONTEXT_KEYS_DECLARED:tenantId,callerId");
    }

    @Test
    void shouldReturnPolicyDisabledWhenNoPolicyConfigured() {
        DsServiceRecord dsService = new DsServiceRecord();
        dsService.setId(2L);
        dsService.setServiceCode("svc_query");
        DsServiceVersionRecord version = new DsServiceVersionRecord();
        version.setVersion(1);
        when(cachePolicyRepository.findByServiceId(2L)).thenReturn(null);

        QueryCacheKeyService.QueryCacheKeySnapshot snapshot = service.build(
                dsService,
                version,
                java.util.Map.of("orderId", 1),
                new QueryRequestContextService.NormalizedQueryRequestContext(null, null, null, java.util.List.of(), java.util.List.of())
        );

        assertThat(snapshot.cacheEnabled()).isFalse();
        assertThat(snapshot.cacheMissReason()).isEqualTo("POLICY_DISABLED");
        assertThat(snapshot.cacheKeyTemplate()).isEqualTo("data-service:{serviceCode}:v{version}:{paramHash}");
        assertThat(snapshot.contextDigest()).isNull();
    }

    @Test
    void shouldGenerateDifferentKeysForVersionAndContextIsolation() {
        DsServiceRecord dsService = new DsServiceRecord();
        dsService.setId(3L);
        dsService.setServiceCode("svc_query");

        DsCachePolicyRecord policy = new DsCachePolicyRecord();
        policy.setServiceId(3L);
        policy.setEnabled(true);
        policy.setTtlSeconds(300);
        policy.setCacheKeyTemplate("data-service:{serviceCode}:v{version}:{paramHash}:{contextDigest}");
        policy.setContextKeysJson("[\"tenantId\",\"callerId\"]");
        when(cachePolicyRepository.findByServiceId(3L)).thenReturn(policy);

        DsServiceVersionRecord version1 = new DsServiceVersionRecord();
        version1.setVersion(1);
        DsServiceVersionRecord version2 = new DsServiceVersionRecord();
        version2.setVersion(2);

        QueryRequestContextService.NormalizedQueryRequestContext contextA =
                new QueryRequestContextService.NormalizedQueryRequestContext(
                        "tenant-a",
                        "caller-a",
                        "trace-a",
                        java.util.List.of("tenantId", "callerId"),
                        java.util.List.of("tenantId", "callerId", "traceId")
                );
        QueryRequestContextService.NormalizedQueryRequestContext contextB =
                new QueryRequestContextService.NormalizedQueryRequestContext(
                        "tenant-b",
                        "caller-a",
                        "trace-b",
                        java.util.List.of("tenantId", "callerId"),
                        java.util.List.of("tenantId", "callerId", "traceId")
                );

        QueryCacheKeyService.QueryCacheKeySnapshot v1ContextA = service.build(
                dsService, version1, java.util.Map.of("orderId", 1), contextA
        );
        QueryCacheKeyService.QueryCacheKeySnapshot v1ContextARepeat = service.build(
                dsService, version1, java.util.Map.of("orderId", 1), contextA
        );
        QueryCacheKeyService.QueryCacheKeySnapshot v2ContextA = service.build(
                dsService, version2, java.util.Map.of("orderId", 1), contextA
        );
        QueryCacheKeyService.QueryCacheKeySnapshot v1ContextB = service.build(
                dsService, version1, java.util.Map.of("orderId", 1), contextB
        );

        assertThat(v1ContextA.cacheKey()).isEqualTo(v1ContextARepeat.cacheKey());
        assertThat(v1ContextA.contextDigest()).isEqualTo(v1ContextARepeat.contextDigest());
        assertThat(v1ContextA.cacheKey()).isNotEqualTo(v2ContextA.cacheKey());
        assertThat(v1ContextA.cacheKey()).isNotEqualTo(v1ContextB.cacheKey());
        assertThat(v1ContextA.contextDigest()).isNotEqualTo(v1ContextB.contextDigest());
    }
}
