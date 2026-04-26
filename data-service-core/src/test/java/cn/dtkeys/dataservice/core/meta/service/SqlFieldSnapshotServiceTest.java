package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlFieldSnapshotServiceTest {

    private final SqlFieldSnapshotService service = new SqlFieldSnapshotService(new ObjectMapper());

    @Test
    void shouldBuildFieldSnapshotForSimpleColumnsAndExpressions() throws Exception {
        String json = service.buildFieldSnapshotJson(
                "SIMPLE_SQL",
                "select o.id, count(*) as total_count, concat(o.order_name, '-x') as order_label from public.orders o"
        );
        JsonNode snapshots = new ObjectMapper().readTree(json);

        assertThat(json).contains("\"fieldName\":\"id\"");
        assertThat(json).contains("\"expression\":\"o.id\"");
        assertThat(json).contains("\"fieldName\":\"total_count\"");
        assertThat(json).contains("\"expression\":\"count(*)\"");
        assertThat(json).contains("\"fieldName\":\"order_label\"");
        assertThat(json).contains("\"expression\":\"concat(o.order_name, '-x')\"");
        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get(0).get("fieldName").asText()).isEqualTo("id");
        assertThat(snapshots.get(0).get("sortOrder").asInt()).isEqualTo(1);
        assertThat(snapshots.get(1).get("fieldName").asText()).isEqualTo("total_count");
        assertThat(snapshots.get(1).get("sortOrder").asInt()).isEqualTo(2);
        assertThat(snapshots.get(2).get("fieldName").asText()).isEqualTo("order_label");
        assertThat(snapshots.get(2).get("sortOrder").asInt()).isEqualTo(3);
    }

    @Test
    void shouldBuildFieldSnapshotForFederatedSqlAliases() {
        String json = service.buildFieldSnapshotJson(
                "FEDERATED_SQL",
                "select o.id as order_id, oi.sku as item_sku from PG_MAIN@public.orders o join PG_ARCHIVE@public.order_items oi on o.id = oi.order_id"
        );

        assertThat(json).contains("\"fieldName\":\"order_id\"");
        assertThat(json).contains("\"expression\":\"o.id\"");
        assertThat(json).contains("\"fieldName\":\"item_sku\"");
        assertThat(json).contains("\"expression\":\"oi.sku\"");
    }

    @Test
    void shouldKeepWildcardField() {
        String json = service.buildFieldSnapshotJson("SIMPLE_SQL", "select o.* from public.orders o");
        assertThat(json).contains("\"fieldName\":\"o.*\"");
        assertThat(json).contains("\"expression\":\"o.*\"");
    }

    @Test
    void shouldRejectExpressionWithoutAsAlias() {
        assertThatThrownBy(() -> service.buildFieldSnapshotJson(
                "SIMPLE_SQL",
                "select count(*) from public.orders"
        ))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("复杂表达式输出列必须显式使用 as 别名");
    }
}
