package cn.dtkeys.dataservice.core.meta.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogicalPlanServiceTest {

    private final LogicalPlanService service =
            new LogicalPlanService(
                    new ObjectMapper(),
                    new SourceSnapshotViewService(new ObjectMapper()),
                    new FilterPushdownService(),
                    new ProjectionPushdownService(),
                    new LocalCompensationGuardService()
            );

    @Test
    void shouldBuildLogicalPlanForFederatedJoinAndAggregate() {
        LogicalPlanService.LogicalPlanSummary summary = service.build(
                "FEDERATED_SQL",
                "select count(*) as total_count from PG_MAIN@public.orders o join PG_ARCHIVE@public.order_items oi on o.id = oi.order_id where oi.order_id = /* orderId */1",
                """
                [{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"},
                 {"connectionCode":"PG_ARCHIVE","schemaName":"public","databaseName":"data_service","tableName":"order_items","alias":"oi","dbType":"POSTGRESQL"}]
                """,
                """
                [{"fieldName":"total_count","expression":"count(*)","sortOrder":1}]
                """,
                1,
                List.of(
                        new SourceCapabilityService.CapabilitySummary(
                                "PG_MAIN",
                                "POSTGRESQL",
                                SourceCapabilityOwnerLevel.CONNECTION,
                                List.of(
                                        new SourceCapabilityService.CapabilityDescriptor(
                                                1L, "PG_MAIN", "POSTGRESQL", SourceCapabilityOwnerLevel.CONNECTION,
                                                SourceCapabilityScopeType.GLOBAL, null, "FILTER_PUSHDOWN", "SUPPORTED", "{}"
                                        ),
                                        new SourceCapabilityService.CapabilityDescriptor(
                                                3L, "PG_MAIN", "POSTGRESQL", SourceCapabilityOwnerLevel.CONNECTION,
                                                SourceCapabilityScopeType.GLOBAL, null, "PROJECT_PUSHDOWN", "SUPPORTED", "{}"
                                        )
                                )
                        ),
                        new SourceCapabilityService.CapabilitySummary(
                                "PG_ARCHIVE",
                                "POSTGRESQL",
                                SourceCapabilityOwnerLevel.CONNECTION,
                                List.of(
                                        new SourceCapabilityService.CapabilityDescriptor(
                                                2L, "PG_ARCHIVE", "POSTGRESQL", SourceCapabilityOwnerLevel.CONNECTION,
                                                SourceCapabilityScopeType.GLOBAL, null, "FILTER_PUSHDOWN", "SUPPORTED", "{}"
                                        ),
                                        new SourceCapabilityService.CapabilityDescriptor(
                                                4L, "PG_ARCHIVE", "POSTGRESQL", SourceCapabilityOwnerLevel.CONNECTION,
                                                SourceCapabilityScopeType.GLOBAL, null, "PROJECT_PUSHDOWN", "SUPPORTED", "{}"
                                        )
                                )
                        )
                )
        );

        assertThat(summary.sourceCount()).isEqualTo(2);
        assertThat(summary.fieldCount()).isEqualTo(1);
        assertThat(summary.paramCount()).isEqualTo(1);
        assertThat(summary.joinReorder().inputs()).hasSize(2);
        assertThat(summary.joinReorder().applied()).isTrue();
        assertThat(summary.joinReorder().reorderedSources().getFirst().connectionCode()).isEqualTo("PG_ARCHIVE");
        assertThat(summary.filterPushdown().extractedConditions()).hasSize(1);
        assertThat(summary.filterPushdown().pushdownConditions()).hasSize(1);
        assertThat(summary.filterPushdown().residualConditions()).isEmpty();
        assertThat(summary.projectionPushdown().requestedFields()).hasSize(1);
        assertThat(summary.projectionPushdown().pushdownFields()).isEmpty();
        assertThat(summary.projectionPushdown().residualFields()).hasSize(1);
        assertThat(summary.projectionPushdown().fieldMapping().consistent()).isTrue();
        assertThat(summary.localCompensation().required()).isTrue();
        assertThat(summary.localCompensation().semanticSafe()).isFalse();
        assertThat(summary.localCompensation().detail()).contains("聚合投影");
        assertThat(summary.nodes()).extracting(LogicalPlanService.LogicalPlanNode::nodeType)
                .containsExactly(
                        LogicalPlanService.LogicalPlanNodeType.SCAN,
                        LogicalPlanService.LogicalPlanNodeType.SCAN,
                        LogicalPlanService.LogicalPlanNodeType.JOIN,
                        LogicalPlanService.LogicalPlanNodeType.FILTER,
                        LogicalPlanService.LogicalPlanNodeType.AGGREGATE,
                        LogicalPlanService.LogicalPlanNodeType.PROJECT,
                        LogicalPlanService.LogicalPlanNodeType.PARAM_BIND
                );
        assertThat(summary.rootNodeId()).isEqualTo("PARAM_BIND_1");
        assertThat(summary.nodes().get(5).detail()).contains("pushdown=0");
        assertThat(summary.nodes().get(5).detail()).contains("residual=1");
    }

    @Test
    void shouldBuildLogicalPlanForSingleSourceQuery() {
        LogicalPlanService.LogicalPlanSummary summary = service.build(
                "SIMPLE_SQL",
                "select o.id, o.order_name from public.orders o where o.id = /* orderId */1",
                """
                [{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"}]
                """,
                """
                [{"fieldName":"id","expression":"o.id","sortOrder":1},
                 {"fieldName":"order_name","expression":"o.order_name","sortOrder":2}]
                """,
                1,
                List.of(
                        new SourceCapabilityService.CapabilitySummary(
                                "PG_MAIN",
                                "POSTGRESQL",
                                SourceCapabilityOwnerLevel.CONNECTION,
                                List.of(
                                        new SourceCapabilityService.CapabilityDescriptor(
                                                1L, "PG_MAIN", "POSTGRESQL", SourceCapabilityOwnerLevel.CONNECTION,
                                                SourceCapabilityScopeType.GLOBAL, null, "FILTER_PUSHDOWN", "SUPPORTED", "{}"
                                        ),
                                        new SourceCapabilityService.CapabilityDescriptor(
                                                2L, "PG_MAIN", "POSTGRESQL", SourceCapabilityOwnerLevel.CONNECTION,
                                                SourceCapabilityScopeType.GLOBAL, null, "PROJECT_PUSHDOWN", "SUPPORTED", "{}"
                                        )
                                )
                        )
                )
        );

        assertThat(summary.sourceCount()).isEqualTo(1);
        assertThat(summary.fieldCount()).isEqualTo(2);
        assertThat(summary.paramCount()).isEqualTo(1);
        assertThat(summary.joinReorder().applied()).isFalse();
        assertThat(summary.filterPushdown().extractedConditions()).hasSize(1);
        assertThat(summary.localCompensation().required()).isFalse();
        assertThat(summary.nodes()).extracting(LogicalPlanService.LogicalPlanNode::nodeType)
                .containsExactly(
                        LogicalPlanService.LogicalPlanNodeType.SCAN,
                        LogicalPlanService.LogicalPlanNodeType.FILTER,
                        LogicalPlanService.LogicalPlanNodeType.PROJECT,
                        LogicalPlanService.LogicalPlanNodeType.PARAM_BIND
                );
        assertThat(summary.rootNodeId()).isEqualTo("PARAM_BIND_1");
    }
}
