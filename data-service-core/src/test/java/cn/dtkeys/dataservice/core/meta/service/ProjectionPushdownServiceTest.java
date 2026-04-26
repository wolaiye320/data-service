package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.service.SqlFieldSnapshotService.FieldSnapshot;
import cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionPushdownServiceTest {

    private final ProjectionPushdownService service = new ProjectionPushdownService();

    @Test
    void shouldPushdownSimpleProjectionWhenCapabilitySupported() {
        ProjectionPushdownService.ProjectionPushdownSummary summary = service.analyze(
                List.of(
                        new FieldSnapshot("id", "o.id", 1),
                        new FieldSnapshot("order_name", "o.order_name", 2)
                ),
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
                                "PROJECT_PUSHDOWN",
                                "SUPPORTED",
                                "{}"
                        ))
                ))
        );

        assertThat(summary.requestedFields()).hasSize(2);
        assertThat(summary.pushdownFields()).hasSize(2);
        assertThat(summary.residualFields()).isEmpty();
        assertThat(summary.fieldMapping().consistent()).isTrue();
        assertThat(summary.rewrittenProjectionFragment()).contains("o.id");
    }

    @Test
    void shouldKeepResidualProjectionWhenCapabilityMissingOrExpressionComplex() {
        ProjectionPushdownService.ProjectionPushdownSummary summary = service.analyze(
                List.of(
                        new FieldSnapshot("order_name", "o.order_name", 1),
                        new FieldSnapshot("order_label", "concat(o.order_name, '-x')", 2)
                ),
                List.of(new SourceSnapshotItemResponse("PG_MAIN", "public", "data_service", "orders", "o", "POSTGRESQL")),
                List.of()
        );

        assertThat(summary.requestedFields()).hasSize(2);
        assertThat(summary.pushdownFields()).isEmpty();
        assertThat(summary.residualFields()).hasSize(2);
        assertThat(summary.fieldMapping().outputFields()).containsExactly("order_name", "order_label");
    }
}
