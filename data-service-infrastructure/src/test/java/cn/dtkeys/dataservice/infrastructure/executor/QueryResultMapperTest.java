package cn.dtkeys.dataservice.infrastructure.executor;

import cn.dtkeys.dataservice.common.exception.QueryExecutionException;
import cn.dtkeys.dataservice.domain.model.DSField;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryResultMapperTest {

    private final QueryResultMapper mapper = new QueryResultMapper();

    @Test
    void shouldMapColumnsByAliasAndSortOrder() {
        DSField amount = field("customer", "amount", "orderAmount", "DECIMAL", 2);
        DSField customerId = field("customer", "customer_id", "customerId", "LONG", 1);
        DSField createdAt = field("customer", "created_at", "createdAt", "DATETIME", 3);

        List<Map<String, Object>> mapped = mapper.map(List.of(Map.of(
            "customer_customer_id", 1001,
            "customer_amount", new BigDecimal("99.50"),
            "customer_created_at", Timestamp.valueOf(LocalDateTime.of(2025, 1, 2, 3, 4, 5))
        )), List.of(amount, customerId, createdAt));

        assertThat(mapped).hasSize(1);
        assertThat(mapped.get(0)).containsExactly(
            Map.entry("customerId", 1001L),
            Map.entry("orderAmount", new BigDecimal("99.50")),
            Map.entry("createdAt", LocalDateTime.of(2025, 1, 2, 3, 4, 5))
        );
    }

    @Test
    void shouldRejectMissingSourceColumn() {
        DSField customerId = field("customer", "customer_id", "customerId", "LONG", 1);

        assertThatThrownBy(() -> mapper.map(List.of(Map.of("wrong_column", 1)), List.of(customerId)))
            .isInstanceOf(QueryExecutionException.class);
    }

    private DSField field(String alias, String sourceColumn, String fieldName, String fieldType, int sortOrder) {
        DSField field = new DSField();
        field.setSourceAlias(alias);
        field.setSourceColumn(sourceColumn);
        field.setFieldName(fieldName);
        field.setFieldType(fieldType);
        field.setSortOrder(sortOrder);
        return field;
    }
}
