package cn.dtkeys.dataservice.core.meta.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FederatedQuerySplitterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FederatedQuerySplitter splitter = new FederatedQuerySplitter(
            objectMapper,
            new SourceSnapshotViewService(objectMapper)
    );
    private final LogicalPlanService logicalPlanService = new LogicalPlanService(
            objectMapper,
            new SourceSnapshotViewService(objectMapper),
            new FilterPushdownService(),
            new ProjectionPushdownService(),
            new LocalCompensationGuardService()
    );

    @Test
    void shouldSplitFederatedSourcesIntoOrderedRemoteTasks() {
        LogicalPlanService.LogicalPlanSummary plan = logicalPlanService.build(
                "FEDERATED_SQL",
                "select o.id as order_id, oi.sku as item_sku from PG_MAIN@public.orders o join PG_ARCHIVE@public.order_items oi on o.id = oi.order_id where o.id = /* orderId */1",
                """
                [{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"},
                 {"connectionCode":"PG_ARCHIVE","schemaName":"public","databaseName":"data_service","tableName":"order_items","alias":"oi","dbType":"POSTGRESQL"}]
                """,
                """
                [{"fieldName":"order_id","expression":"o.id","sortOrder":1},{"fieldName":"item_sku","expression":"oi.sku","sortOrder":2}]
                """,
                1,
                List.of()
        );

        FederatedQuerySplitter.FederatedSplitPlan splitPlan = splitter.split(
                "select o.id as order_id, oi.sku as item_sku from PG_MAIN@public.orders o join PG_ARCHIVE@public.order_items oi on o.id = oi.order_id where o.id = /* orderId */1",
                """
                [{"connectionCode":"PG_MAIN","schemaName":"public","databaseName":"data_service","tableName":"orders","alias":"o","dbType":"POSTGRESQL"},
                 {"connectionCode":"PG_ARCHIVE","schemaName":"public","databaseName":"data_service","tableName":"order_items","alias":"oi","dbType":"POSTGRESQL"}]
                """,
                """
                [{"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}]
                """,
                plan
        );

        assertThat(splitPlan.tasks()).hasSize(2);
        assertThat(splitPlan.tasks().getFirst().connectionCode()).isEqualTo("PG_MAIN");
        assertThat(splitPlan.tasks().get(1).connectionCode()).isEqualTo("PG_ARCHIVE");
        assertThat(splitPlan.tasks().getFirst().sql()).contains("select * from public.orders o");
        assertThat(splitPlan.joinConditions()).hasSize(1);
        assertThat(splitPlan.joinConditions().getFirst().expression()).isEqualTo("o.id = oi.order_id");
        assertThat(splitPlan.residualFilters()).containsExactly("o.id = /* orderId */1");
    }
}
