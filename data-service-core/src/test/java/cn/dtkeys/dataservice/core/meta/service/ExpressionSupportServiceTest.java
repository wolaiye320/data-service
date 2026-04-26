package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsDialectRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpressionSupportServiceTest {

    private final DsDialectRuleRepository repository = mock(DsDialectRuleRepository.class);
    private final DialectRuleService dialectRuleService = new DialectRuleService(repository, new ObjectMapper());
    private final ExpressionSupportService service = new ExpressionSupportService(dialectRuleService, new ObjectMapper());

    @Test
    void shouldClassifySupportedRewritableAndUnsupportedExpressions() {
        DsConnectionRecord connection = new DsConnectionRecord();
        connection.setId(1L);
        connection.setConnectionCode("PG_MAIN");
        connection.setDbType("POSTGRESQL");

        when(repository.findEnabledByDbType("POSTGRESQL")).thenReturn(java.util.List.of());
        when(repository.findEnabledByConnectionId(1L)).thenReturn(java.util.List.of());

        ExpressionSupportService.ExpressionSupportSummary summary = service.analyze("""
                select
                    case when o.id = 1 then 'A' else 'B' end as bucket,
                    cast(o.id as bigint) as id_cast,
                    count(*) as total_count
                from public.orders o
                """, connection);

        assertThat(summary.items()).hasSize(3);
        assertThat(summary.supportedCount()).isEqualTo(3);
        assertThat(summary.rewritableCount()).isZero();
        assertThat(summary.unsupportedCount()).isZero();
    }

    @Test
    void shouldRejectUnsupportedJsonCastExpression() {
        assertThatThrownBy(() -> service.validateOrThrow("""
                select cast(payload as json) as payload_json from public.orders
                """, null))
                .isInstanceOf(DataServiceException.class)
                .hasMessageContaining("复杂表达式不受支持")
                .hasMessageContaining("type=CAST")
                .hasMessageContaining("JSON/复杂类型转换");
    }

    @Test
    void shouldMarkDateTimeAndStringExpressionsAsRewritableWhenDialectRuleApplies() {
        DsConnectionRecord connection = new DsConnectionRecord();
        connection.setId(2L);
        connection.setConnectionCode("ORACLE_MAIN");
        connection.setDbType("ORACLE");

        cn.dtkeys.dataservice.core.meta.domain.DsDialectRuleRecord rule =
                new cn.dtkeys.dataservice.core.meta.domain.DsDialectRuleRecord();
        rule.setId(20L);
        rule.setDbType("ORACLE");
        rule.setRuleCode("STRING_CONCAT");
        rule.setRuleType("FUNCTION_MAPPING");
        rule.setRuleConfig("{\"source\":\"concat\",\"target\":\"nvl\"}");

        when(repository.findEnabledByDbType("ORACLE")).thenReturn(java.util.List.of(rule));
        when(repository.findEnabledByConnectionId(2L)).thenReturn(java.util.List.of());

        ExpressionSupportService.ExpressionSupportSummary summary = service.analyze("""
                select
                    date_trunc('day', current_timestamp) as biz_day,
                    concat(order_name, '-x') as order_label
                from public.orders
                """, connection);

        assertThat(summary.items()).hasSize(2);
        assertThat(summary.supportedCount()).isZero();
        assertThat(summary.rewritableCount()).isEqualTo(2);
        assertThat(summary.unsupportedCount()).isZero();
        assertThat(summary.items().get(0).expressionType())
                .isEqualTo(ExpressionSupportService.ExpressionType.DATETIME);
        assertThat(summary.items().get(0).status())
                .isEqualTo(ExpressionSupportService.SupportStatus.REWRITABLE);
        assertThat(summary.items().get(1).expressionType())
                .isEqualTo(ExpressionSupportService.ExpressionType.STRING);
        assertThat(summary.items().get(1).status())
                .isEqualTo(ExpressionSupportService.SupportStatus.REWRITABLE);
    }
}
