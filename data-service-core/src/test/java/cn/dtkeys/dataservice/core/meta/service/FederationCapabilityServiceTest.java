package cn.dtkeys.dataservice.core.meta.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FederationCapabilityServiceTest {

    private final FederationCapabilityService service = new FederationCapabilityService(new ObjectMapper());

    @Test
    void shouldRejectWhenRequiredFederationCapabilityMissing() {
        SourceCapabilityService.CapabilitySummary pgSummary = new SourceCapabilityService.CapabilitySummary(
                "PG_MAIN",
                "POSTGRESQL",
                SourceCapabilityOwnerLevel.CONNECTION,
                List.of()
        );
        SourceCapabilityService.CapabilitySummary mysqlSummary = new SourceCapabilityService.CapabilitySummary(
                "MY_MAIN",
                "MYSQL",
                SourceCapabilityOwnerLevel.CONNECTION,
                List.of()
        );

        assertThatThrownBy(() -> service.validateOrThrow(
                "select * from PG_MAIN@public.orders o join MY_MAIN@test.order_items oi on o.id = oi.order_id",
                List.of(
                        new cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse("PG_MAIN", "public", "db1", "orders", "o", "POSTGRESQL"),
                        new cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse("MY_MAIN", "test", "db2", "order_items", "oi", "MYSQL")
                ),
                List.of(pgSummary, mysqlSummary),
                new ExpressionSupportService.ExpressionSupportSummary(List.of(), 0, 0, 0)
        ))
                .isInstanceOf(cn.dtkeys.dataservice.core.error.DataServiceException.class)
                .hasMessageContaining("联邦能力校验失败");
    }
}
