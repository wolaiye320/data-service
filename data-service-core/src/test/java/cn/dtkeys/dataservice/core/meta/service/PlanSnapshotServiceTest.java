package cn.dtkeys.dataservice.core.meta.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanSnapshotServiceTest {

    private final PlanSnapshotService service = new PlanSnapshotService(new ObjectMapper());

    @Test
    void shouldSerializeAndDeserializePlanSnapshot() {
        LogicalPlanService.LogicalPlanSummary logicalPlan = new LogicalPlanService(
                new ObjectMapper(),
                new SourceSnapshotViewService(new ObjectMapper()),
                new FilterPushdownService(),
                new ProjectionPushdownService(),
                new LocalCompensationGuardService()
        ).build(
                "SIMPLE_SQL",
                "select o.id from public.orders o where o.id = /* orderId */1",
                """
                [{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"}]
                """,
                """
                [{"fieldName":"id","expression":"o.id","sortOrder":1}]
                """,
                1,
                List.of()
        );

        PlanSnapshotService.PlanSnapshot snapshot = new PlanSnapshotService.PlanSnapshot(
                "PUBLISH",
                "SIMPLE_SQL",
                1,
                1,
                List.of("PARSE", "VALIDATE", "PLAN"),
                List.of(),
                logicalPlan,
                "{\"result\":\"PASS\"}"
        );

        String json = service.toJson(snapshot);
        PlanSnapshotService.PlanSnapshot restored = service.fromJson(json);

        assertThat(json).contains("\"stage\":\"PUBLISH\"");
        assertThat(json).contains("\"sqlType\":\"SIMPLE_SQL\"");
        assertThat(restored.stage()).isEqualTo("PUBLISH");
        assertThat(restored.sqlType()).isEqualTo("SIMPLE_SQL");
        assertThat(restored.sourceCount()).isEqualTo(1);
        assertThat(restored.paramCount()).isEqualTo(1);
        assertThat(restored.stages()).containsExactly("PARSE", "VALIDATE", "PLAN");
        assertThat(restored.logicalPlan().rootNodeId()).isEqualTo("PARAM_BIND_1");
        assertThat(restored.diagnosticSummary()).isEqualTo("{\"result\":\"PASS\"}");
    }
}
