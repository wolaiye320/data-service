package cn.dtkeys.dataservice.core.meta.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCompensationGuardServiceTest {

    private final LocalCompensationGuardService service = new LocalCompensationGuardService();

    @Test
    void shouldAllowResidualFilterAndProjectionWithinSafeThreshold() {
        LocalCompensationGuardService.LocalCompensationSummary summary = service.analyze(
                "select id, order_name from PG_MAIN@public.orders where id = /* orderId */1",
                1,
                100,
                new FilterPushdownService.FilterPushdownSummary(
                        List.of(new FilterPushdownService.FilterCondition("id = /* orderId */1", "PG_MAIN", "o", false)),
                        List.of(),
                        List.of(new FilterPushdownService.FilterCondition("id = /* orderId */1", "PG_MAIN", "o", false)),
                        null,
                        "Residual filter"
                ),
                new ProjectionPushdownService.ProjectionPushdownSummary(
                        List.of(new ProjectionPushdownService.ProjectionField("order_name", "order_name", 1, true)),
                        List.of(),
                        List.of(new ProjectionPushdownService.ProjectionField("order_name", "order_name", 1, true)),
                        null,
                        new ProjectionPushdownService.FieldMappingSummary(
                                List.of("order_name"),
                                List.of(),
                                List.of("order_name"),
                                true,
                                "字段顺序与别名可按快照稳定映射"
                        )
                )
        );

        assertThat(summary.required()).isTrue();
        assertThat(summary.semanticSafe()).isTrue();
        assertThat(summary.resourceSafe()).isTrue();
        assertThat(summary.allowedOperations()).containsExactly(
                LocalCompensationGuardService.LocalCompensationOperation.RESIDUAL_FILTER,
                LocalCompensationGuardService.LocalCompensationOperation.RESIDUAL_PROJECT_MAPPING
        );
    }

    @Test
    void shouldRejectAggregateProjectionLocalCompensation() {
        assertThatThrownBy(() -> service.validateOrThrow(
                "select count(*) as total_count from PG_MAIN@public.orders",
                1,
                100,
                new FilterPushdownService.FilterPushdownSummary(List.of(), List.of(), List.of(), null, "none"),
                new ProjectionPushdownService.ProjectionPushdownSummary(
                        List.of(new ProjectionPushdownService.ProjectionField("total_count", "count(*)", 1, false)),
                        List.of(),
                        List.of(new ProjectionPushdownService.ProjectionField("total_count", "count(*)", 1, false)),
                        null,
                        new ProjectionPushdownService.FieldMappingSummary(
                                List.of("total_count"),
                                List.of(),
                                List.of("total_count"),
                                true,
                                "字段顺序与别名可按快照稳定映射"
                        )
                )
        ))
                .isInstanceOf(cn.dtkeys.dataservice.core.error.DataServiceException.class)
                .hasMessageContaining("本地补算校验失败")
                .hasMessageContaining("SEMANTIC");
    }
}
