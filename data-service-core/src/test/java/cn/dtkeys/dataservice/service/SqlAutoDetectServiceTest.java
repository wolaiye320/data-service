package cn.dtkeys.dataservice.service;

import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.repository.DSCatalogRepository;
import cn.dtkeys.dataservice.repository.DSConnectionRepository;
import cn.dtkeys.dataservice.web.dto.admin.service.SqlAutoDetectResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlAutoDetectServiceTest {

    @Mock
    private DSConnectionRepository dsConnectionRepository;

    @Mock
    private DSCatalogRepository dsCatalogRepository;

    @Test
    void shouldDetectSourcesParamsAndFieldsFromFederatedSql() {
        DSConnection connection = buildConnection(1L);
        DSCatalog catalog = buildCatalog(11L, 1L);
        when(dsConnectionRepository.findAll()).thenReturn(List.of(connection));
        when(dsCatalogRepository.findByConnectionId(1L)).thenReturn(List.of(catalog));

        SqlAutoDetectService service = new SqlAutoDetectService(dsConnectionRepository, dsCatalogRepository);

        SqlAutoDetectResponse response = service.detect(
            """
                select c.customer_id as customerId, c.customer_name, sum(o.order_amount) as totalAmount
                from public.customer_base c
                join public.customer_order o on c.customer_id = o.customer_id
                where c.customer_id = :customerId and o.active = :active
                """,
            "FEDERATED_SQL",
            null,
            null
        );

        assertThat(response.sqlType()).isEqualTo("FEDERATED_SQL");
        assertThat(response.serviceType()).isEqualTo("FEDERATED_QUERY");
        assertThat(response.executionMode()).isEqualTo("REMOTE_PLUS_LOCAL");
        assertThat(response.planStatus()).isEqualTo("PLANNED");
        assertThat(response.sources()).hasSize(2);
        assertThat(response.sources().get(0).sourceAlias()).isEqualTo("c");
        assertThat(response.sources().get(0).sourceValue()).isEqualTo("customer_base");
        assertThat(response.sources().get(0).connectionResolved()).isTrue();
        assertThat(response.sources().get(0).catalogResolved()).isTrue();
        assertThat(response.sources().get(1).sourceAlias()).isEqualTo("o");
        assertThat(response.params())
            .extracting(SqlAutoDetectResponse.DetectedParamView::paramName,
                SqlAutoDetectResponse.DetectedParamView::paramType)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("customerId", "LONG"),
                org.assertj.core.groups.Tuple.tuple("active", "BOOLEAN")
            );
        assertThat(response.fields())
            .extracting(SqlAutoDetectResponse.DetectedFieldView::fieldName,
                SqlAutoDetectResponse.DetectedFieldView::sourceAlias,
                SqlAutoDetectResponse.DetectedFieldView::sourceColumn,
                SqlAutoDetectResponse.DetectedFieldView::fieldType)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("customerId", "c", "customer_id", "LONG"),
                org.assertj.core.groups.Tuple.tuple("customerName", "c", "customer_name", "STRING"),
                org.assertj.core.groups.Tuple.tuple("totalAmount", null, "totalAmount", "DECIMAL")
            );
    }

    @Test
    void shouldLeaveConnectionUnresolvedWhenMultipleConnectionsExist() {
        when(dsConnectionRepository.findAll()).thenReturn(List.of(buildConnection(1L), buildConnection(2L)));

        SqlAutoDetectService service = new SqlAutoDetectService(dsConnectionRepository, dsCatalogRepository);

        SqlAutoDetectResponse response = service.detect(
            "select customer_id from customer_base where customer_id = :customerId",
            "SIMPLE_SQL",
            null,
            null
        );

        assertThat(response.sqlType()).isEqualTo("SIMPLE_SQL");
        assertThat(response.sources()).singleElement().satisfies(source -> {
            assertThat(source.connectionId()).isNull();
            assertThat(source.catalogId()).isNull();
            assertThat(source.connectionResolved()).isFalse();
            assertThat(source.catalogResolved()).isFalse();
        });
    }

    private DSConnection buildConnection(Long id) {
        DSConnection connection = new DSConnection();
        connection.setId(id);
        connection.setConnectionCode("pg_" + id);
        connection.setConnectionName("连接" + id);
        connection.setDbType("POSTGRESQL");
        return connection;
    }

    private DSCatalog buildCatalog(Long id, Long connectionId) {
        DSCatalog catalog = new DSCatalog();
        catalog.setId(id);
        catalog.setConnectionId(connectionId);
        catalog.setCatalogCode("public");
        catalog.setCatalogName("public");
        catalog.setCatalogType("SCHEMA");
        catalog.setCatalogValue("public");
        return catalog;
    }
}
