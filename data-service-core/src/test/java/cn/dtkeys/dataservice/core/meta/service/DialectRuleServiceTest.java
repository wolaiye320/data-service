package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsDialectRuleRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsDialectRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialectRuleServiceTest {

    private final DsDialectRuleRepository repository = mock(DsDialectRuleRepository.class);
    private final DialectRuleService service = new DialectRuleService(repository, new ObjectMapper());

    @Test
    void shouldPreferConnectionRuleOverDbTypeDefault() {
        DsConnectionRecord connection = new DsConnectionRecord();
        connection.setId(1L);
        connection.setConnectionCode("ORACLE_MAIN");
        connection.setDbType("ORACLE");

        DsDialectRuleRecord dbDefaultRule = new DsDialectRuleRecord();
        dbDefaultRule.setId(10L);
        dbDefaultRule.setDbType("ORACLE");
        dbDefaultRule.setRuleCode("STRING_CONCAT");
        dbDefaultRule.setRuleType("FUNCTION_MAPPING");
        dbDefaultRule.setRuleConfig("{\"source\":\"concat\",\"target\":\"||\"}");

        DsDialectRuleRecord connectionRule = new DsDialectRuleRecord();
        connectionRule.setId(11L);
        connectionRule.setConnectionId(1L);
        connectionRule.setDbType("ORACLE");
        connectionRule.setRuleCode("STRING_CONCAT");
        connectionRule.setRuleType("FUNCTION_MAPPING");
        connectionRule.setRuleConfig("{\"source\":\"concat\",\"target\":\"concat\"}");

        when(repository.findEnabledByDbType("ORACLE")).thenReturn(List.of(dbDefaultRule));
        when(repository.findEnabledByConnectionId(1L)).thenReturn(List.of(connectionRule));

        List<DialectRuleService.EffectiveDialectRule> rules = service.loadEffectiveRules(connection);
        DialectRuleService.RewriteResult rewriteResult =
                service.rewriteSql("select concat(order_name, '-x') from dual", connection);

        assertThat(rules).hasSize(1);
        assertThat(rules.getFirst().priority()).isEqualTo(DialectRuleService.RulePriority.CONNECTION_OVERRIDE);
        assertThat(rewriteResult.sql()).isEqualTo("select concat(order_name, '-x') from dual");
        assertThat(rewriteResult.appliedRuleCount()).isZero();
    }

    @Test
    void shouldRewriteSqlWhenOnlyDbTypeDefaultMatches() {
        DsConnectionRecord connection = new DsConnectionRecord();
        connection.setId(2L);
        connection.setConnectionCode("ORACLE_FALLBACK");
        connection.setDbType("ORACLE");

        DsDialectRuleRecord dbDefaultRule = new DsDialectRuleRecord();
        dbDefaultRule.setId(20L);
        dbDefaultRule.setDbType("ORACLE");
        dbDefaultRule.setRuleCode("STRING_CONCAT");
        dbDefaultRule.setRuleType("FUNCTION_MAPPING");
        dbDefaultRule.setRuleConfig("{\"source\":\"concat\",\"target\":\"nvl\"}");

        when(repository.findEnabledByDbType("ORACLE")).thenReturn(List.of(dbDefaultRule));
        when(repository.findEnabledByConnectionId(2L)).thenReturn(List.of());

        DialectRuleService.RewriteResult rewriteResult =
                service.rewriteSql("select concat(order_name, '-x') from dual", connection);

        assertThat(rewriteResult.sql()).isEqualTo("select nvl(order_name, '-x') from dual");
        assertThat(rewriteResult.appliedRuleCount()).isEqualTo(1);
        assertThat(rewriteResult.effectiveRules()).hasSize(1);
        assertThat(rewriteResult.effectiveRules().getFirst().priority())
                .isEqualTo(DialectRuleService.RulePriority.DB_TYPE_DEFAULT);
    }
}
