package cn.dtkeys.dataservice.infrastructure.executor;

import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.domain.model.DSParam;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryParameterBinderTest {

    private final QueryParameterBinder binder = new QueryParameterBinder();

    @Test
    void shouldBindAndConvertParameters() {
        DSParam customerId = param("customerId", "customerId", "LONG", true, null);
        DSParam enabled = param("enabled", "enabled", "BOOLEAN", false, "true");
        DSParam tags = param("tags", "tags", "LIST", false, null);

        BoundQuery boundQuery = binder.bind(
            "select * from customer where customer_id = :customerId and enabled = :enabled and tag in (:tags)",
            List.of(customerId, enabled, tags),
            Map.of("customerId", "1001", "tags", List.of("VIP", "A"))
        );

        assertThat(boundQuery.params())
            .containsEntry("customerId", 1001L)
            .containsEntry("enabled", true);
        assertThat(boundQuery.params().get("tags")).isEqualTo(List.of("VIP", "A"));
    }

    @Test
    void shouldRejectMissingRequiredAndUnknownParameters() {
        DSParam customerId = param("customerId", "customerId", "LONG", true, null);

        assertThatThrownBy(() -> binder.bind(
            "select * from customer where customer_id = :customerId",
            List.of(customerId),
            Map.of()
        )).isInstanceOf(ParamInvalidException.class);

        assertThatThrownBy(() -> binder.bind(
            "select * from customer where customer_id = :customerId",
            List.of(customerId),
            Map.of("customerId", 1, "unknown", "x")
        )).isInstanceOf(ParamInvalidException.class);
    }

    @Test
    void shouldRejectPlaceholderMismatchAndTypeMismatch() {
        DSParam customerId = param("customerId", "customerId", "LONG", true, null);

        assertThatThrownBy(() -> binder.bind(
            "select * from customer where customer_id = :customerId and status = :status",
            List.of(customerId),
            Map.of("customerId", 1)
        )).isInstanceOf(ParamInvalidException.class);

        assertThatThrownBy(() -> binder.bind(
            "select * from customer where customer_id = :customerId",
            List.of(customerId),
            Map.of("customerId", "abc")
        )).isInstanceOf(ParamInvalidException.class);
    }

    private DSParam param(String name, String placeholder, String type, boolean required, String defaultValue) {
        DSParam param = new DSParam();
        param.setParamName(name);
        param.setSqlPlaceholder(placeholder);
        param.setParamType(type);
        param.setRequired(required);
        param.setDefaultValue(defaultValue);
        return param;
    }
}
