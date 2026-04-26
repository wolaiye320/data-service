package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlSourceSnapshotServiceTest {

    private final DsConnectionRepository connectionRepository = mock(DsConnectionRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SqlSourceSnapshotService service = new SqlSourceSnapshotService(connectionRepository, objectMapper);

    @Test
    void shouldBuildSimpleSqlSnapshotsWithDefaultConnection() throws Exception {
        when(connectionRepository.findByConnectionCode("PG_MAIN"))
                .thenReturn(connection("PG_MAIN", "POSTGRESQL", "data_service"));

        JsonNode snapshots = objectMapper.readTree(service.buildSourceSnapshotJson(
                "SIMPLE_SQL",
                """
                        select o.id, oi.sku
                        from public.orders o
                        join public.order_items oi on oi.order_id = o.id
                        """,
                "PG_MAIN"
        ));

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).get("connectionCode").asText()).isEqualTo("PG_MAIN");
        assertThat(snapshots.get(0).get("schemaName").asText()).isEqualTo("public");
        assertThat(snapshots.get(0).get("databaseName").asText()).isEqualTo("data_service");
        assertThat(snapshots.get(0).get("tableName").asText()).isEqualTo("orders");
        assertThat(snapshots.get(0).get("alias").asText()).isEqualTo("o");
        assertThat(snapshots.get(0).get("dbType").asText()).isEqualTo("POSTGRESQL");
        assertThat(snapshots.get(1).get("tableName").asText()).isEqualTo("order_items");
        assertThat(snapshots.get(1).get("alias").asText()).isEqualTo("oi");
    }

    @Test
    void shouldBuildFederatedSnapshotsWithStableOrder() throws Exception {
        when(connectionRepository.findByConnectionCode("PG_MAIN"))
                .thenReturn(connection("PG_MAIN", "POSTGRESQL", "data_service"));
        when(connectionRepository.findByConnectionCode("MYSQL_BI"))
                .thenReturn(connection("MYSQL_BI", "MYSQL", "ignored_by_mysql_rule"));

        JsonNode snapshots = objectMapper.readTree(service.buildSourceSnapshotJson(
                "FEDERATED_SQL",
                """
                        select o.id, c.customer_name
                        from PG_MAIN@public.orders o
                        join MYSQL_BI@crm.customers c on c.id = o.id
                        """,
                null
        ));

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).get("connectionCode").asText()).isEqualTo("PG_MAIN");
        assertThat(snapshots.get(0).get("schemaName").asText()).isEqualTo("public");
        assertThat(snapshots.get(0).get("databaseName").asText()).isEqualTo("data_service");
        assertThat(snapshots.get(0).get("tableName").asText()).isEqualTo("orders");
        assertThat(snapshots.get(0).get("alias").asText()).isEqualTo("o");
        assertThat(snapshots.get(1).get("connectionCode").asText()).isEqualTo("MYSQL_BI");
        assertThat(snapshots.get(1).get("schemaName").isNull()).isTrue();
        assertThat(snapshots.get(1).get("databaseName").asText()).isEqualTo("crm");
        assertThat(snapshots.get(1).get("tableName").asText()).isEqualTo("customers");
        assertThat(snapshots.get(1).get("alias").asText()).isEqualTo("c");
        assertThat(snapshots.get(1).get("dbType").asText()).isEqualTo("MYSQL");
    }

    @Test
    void shouldRejectFederatedMultiTableQueryWithoutAlias() {
        assertThatThrownBy(() -> service.validateFederatedAliases(
                "FEDERATED_SQL",
                """
                        select *
                        from PG_MAIN@public.orders o
                        join MYSQL_BI@crm.customers on customers.id = o.id
                        """
        ))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("联邦 SQL 多表查询必须显式指定表别名");
    }

    @Test
    void shouldBuildSnapshotsFromCteSubqueryAndUnionBranches() throws Exception {
        when(connectionRepository.findByConnectionCode("PG_MAIN"))
                .thenReturn(connection("PG_MAIN", "POSTGRESQL", "data_service"));
        when(connectionRepository.findByConnectionCode("PG_ARCHIVE"))
                .thenReturn(connection("PG_ARCHIVE", "POSTGRESQL", "archive_service"));

        JsonNode snapshots = objectMapper.readTree(service.buildSourceSnapshotJson(
                "FEDERATED_SQL",
                """
                        with active_orders as (
                            select o.id
                            from PG_MAIN@public.orders o
                        ),
                        archived_items as (
                            select oi.order_id
                            from PG_ARCHIVE@public.order_items oi
                        )
                        select union_src.id
                        from (
                            select ao.id
                            from active_orders ao
                            union all
                            select ai.order_id
                            from archived_items ai
                        ) union_src
                        join PG_MAIN@public.orders final_o on final_o.id = union_src.id
                        """,
                null
        ));

        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get(0).get("connectionCode").asText()).isEqualTo("PG_MAIN");
        assertThat(snapshots.get(0).get("tableName").asText()).isEqualTo("orders");
        assertThat(snapshots.get(0).get("alias").asText()).isEqualTo("o");
        assertThat(snapshots.get(1).get("connectionCode").asText()).isEqualTo("PG_ARCHIVE");
        assertThat(snapshots.get(1).get("tableName").asText()).isEqualTo("order_items");
        assertThat(snapshots.get(1).get("alias").asText()).isEqualTo("oi");
        assertThat(snapshots.get(2).get("connectionCode").asText()).isEqualTo("PG_MAIN");
        assertThat(snapshots.get(2).get("tableName").asText()).isEqualTo("orders");
        assertThat(snapshots.get(2).get("alias").asText()).isEqualTo("final_o");
    }

    private DsConnectionRecord connection(String connectionCode, String dbType, String databaseName) {
        DsConnectionRecord record = new DsConnectionRecord();
        record.setConnectionCode(connectionCode);
        record.setDbType(dbType);
        record.setConnectionConfigJson("{\"databaseName\":\"" + databaseName + "\"}");
        return record;
    }
}
