package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilterPushdownServiceTest {

    private final FilterPushdownService service = new FilterPushdownService();

    @Test
    void shouldPushdownFilterWhenCapabilityIsSupported() {
        FilterPushdownService.FilterPushdownSummary summary = service.analyze(
                "select * from public.orders o where o.id = /* orderId */1",
                List.of(new SourceSnapshotItemResponse("PG_MAIN", "public", "data_service", "orders", "o", "POSTGRESQL")),
                List.of(new SourceCapabilityService.CapabilitySummary(
                        "PG_MAIN",
                        "POSTGRESQL",
                        SourceCapabilityOwnerLevel.CONNECTION,
                        List.of(new SourceCapabilityService.CapabilityDescriptor(
                                1L,
                                "PG_MAIN",
                                "POSTGRESQL",
                                SourceCapabilityOwnerLevel.CONNECTION,
                                SourceCapabilityScopeType.GLOBAL,
                                null,
                                "FILTER_PUSHDOWN",
                                "SUPPORTED",
                                "{}"
                        ))
                ))
        );

        assertThat(summary.extractedConditions()).hasSize(1);
        assertThat(summary.pushdownConditions()).hasSize(1);
        assertThat(summary.residualConditions()).isEmpty();
        assertThat(summary.rewrittenPushdownFragment()).contains("o.id = /* orderId */1");
    }

    @Test
    void shouldKeepResidualWhenCapabilityValueIsNotSupported() {
        FilterPushdownService.FilterPushdownSummary summary = service.analyze(
                "select * from public.orders o where o.id = /* orderId */1",
                List.of(new SourceSnapshotItemResponse("PG_MAIN", "public", "data_service", "orders", "o", "POSTGRESQL")),
                List.of(new SourceCapabilityService.CapabilitySummary(
                        "PG_MAIN",
                        "POSTGRESQL",
                        SourceCapabilityOwnerLevel.CONNECTION,
                        List.of(new SourceCapabilityService.CapabilityDescriptor(
                                1L,
                                "PG_MAIN",
                                "POSTGRESQL",
                                SourceCapabilityOwnerLevel.CONNECTION,
                                SourceCapabilityScopeType.GLOBAL,
                                null,
                                "FILTER_PUSHDOWN",
                                "UNSUPPORTED",
                                "{}"
                        ))
                ))
        );

        assertThat(summary.extractedConditions()).hasSize(1);
        assertThat(summary.pushdownConditions()).isEmpty();
        assertThat(summary.residualConditions()).hasSize(1);
        assertThat(summary.residualReason()).contains("Residual filters");
    }
}
