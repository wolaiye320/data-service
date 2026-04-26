package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsSourceCapabilityRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsSourceCapabilityRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceCapabilityServiceTest {

    private final DsSourceCapabilityRepository repository = mock(DsSourceCapabilityRepository.class);
    private final SourceCapabilityService service = new SourceCapabilityService(repository);

    @Test
    void shouldNormalizeSortAndSummarizeCapabilities() {
        DsConnectionRecord connection = new DsConnectionRecord();
        connection.setId(10L);
        connection.setConnectionCode("PG_MAIN");
        connection.setDbType("POSTGRESQL");

        DsSourceCapabilityRecord schemaCapability = new DsSourceCapabilityRecord();
        schemaCapability.setId(2L);
        schemaCapability.setConnectionId(10L);
        schemaCapability.setDbType("POSTGRESQL");
        schemaCapability.setCapabilityCode("FILTER_PUSHDOWN");
        schemaCapability.setCapabilityValue("SUPPORTED");
        schemaCapability.setScope("SCHEMA");
        schemaCapability.setScopeValue("public");
        schemaCapability.setCapabilityDetailJson("{\"operators\":[\"=\"]}");

        DsSourceCapabilityRecord globalCapability = new DsSourceCapabilityRecord();
        globalCapability.setId(1L);
        globalCapability.setConnectionId(10L);
        globalCapability.setDbType("POSTGRESQL");
        globalCapability.setCapabilityCode("FILTER_PUSHDOWN");
        globalCapability.setCapabilityValue("SUPPORTED");
        globalCapability.setScope("GLOBAL");
        globalCapability.setCapabilityDetailJson("{\"operators\":[\"in\"]}");

        DsSourceCapabilityRecord customCapability = new DsSourceCapabilityRecord();
        customCapability.setId(3L);
        customCapability.setConnectionId(10L);
        customCapability.setDbType("POSTGRESQL");
        customCapability.setCapabilityCode("JOIN");
        customCapability.setCapabilityValue("SUPPORTED");
        customCapability.setScope("CATALOG");
        customCapability.setScopeValue("sales");

        when(repository.findEnabledByConnectionId(10L))
                .thenReturn(List.of(schemaCapability, customCapability, globalCapability));

        List<SourceCapabilityService.CapabilityDescriptor> capabilities = service.loadCapabilities(connection);
        SourceCapabilityService.CapabilitySummary summary = service.summarize(connection);

        assertThat(capabilities).hasSize(3);
        assertThat(capabilities.get(0).capabilityCode()).isEqualTo("FILTER_PUSHDOWN");
        assertThat(capabilities.get(0).scopeType()).isEqualTo(SourceCapabilityScopeType.GLOBAL);
        assertThat(capabilities.get(0).ownerLevel()).isEqualTo(SourceCapabilityOwnerLevel.CONNECTION);
        assertThat(capabilities.get(1).scopeType()).isEqualTo(SourceCapabilityScopeType.SCHEMA);
        assertThat(capabilities.get(1).scopeValue()).isEqualTo("public");
        assertThat(capabilities.get(2).capabilityCode()).isEqualTo("JOIN");
        assertThat(capabilities.get(2).scopeType()).isEqualTo(SourceCapabilityScopeType.CUSTOM);
        assertThat(capabilities.get(2).scopeValue()).isEqualTo("sales");

        assertThat(summary.connectionCode()).isEqualTo("PG_MAIN");
        assertThat(summary.dbType()).isEqualTo("POSTGRESQL");
        assertThat(summary.ownerLevel()).isEqualTo(SourceCapabilityOwnerLevel.CONNECTION);
        assertThat(summary.capabilityCount()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyWhenConnectionMissing() {
        assertThat(service.loadCapabilities(null)).isEmpty();
        assertThat(service.hasEnabledCapabilities(null)).isFalse();
    }
}
