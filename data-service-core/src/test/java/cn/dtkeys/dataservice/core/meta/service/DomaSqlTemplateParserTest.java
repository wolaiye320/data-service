package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomaSqlTemplateParserTest {

    private final DomaSqlTemplateParser parser = new DomaSqlTemplateParser(new ObjectMapper());

    @Test
    void shouldParsePlaceholderConditionAndCollectionParameter() {
        DomaSqlTemplateParser.ParseResult result = parser.parse("""
                select *
                from public.orders
                where id = /* orderId */1
                /*%if orderName != null */
                  and order_name = /* orderName */'demo'
                /*%end*/
                  and id in /* orderIds */(1,2,3)
                """);

        assertThat(result.params()).hasSize(3);
        assertThat(result.params().get(0).paramName()).isEqualTo("orderId");
        assertThat(result.params().get(0).defaultValue()).isEqualTo("1");
        assertThat(result.params().get(1).paramName()).isEqualTo("orderName");
        assertThat(result.params().get(1).defaultValue()).isEqualTo("'demo'");
        assertThat(result.params().get(2).paramName()).isEqualTo("orderIds");
        assertThat(result.params().get(2).collection()).isTrue();
        assertThat(result.params().get(2).defaultValue()).isEqualTo("(1,2,3)");
        assertThat(result.paramSnapshotJson()).contains("\"paramName\":\"orderId\"");
        assertThat(result.paramSnapshotJson()).contains("\"collection\":true");
    }

    @Test
    void shouldRejectOrdinaryBlockComment() {
        assertThatThrownBy(() -> parser.parse("""
                select * from public.orders /* comment */
                """))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("不允许普通块注释，请使用 Doma 参数或条件块语法");
    }

    @Test
    void shouldRejectUnclosedConditionBlock() {
        assertThatThrownBy(() -> parser.parse("""
                select *
                from public.orders
                /*%if orderId != null */
                where id = /* orderId */1
                """))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("Doma 条件块未正确闭合");
    }

    @Test
    void shouldParseLiteralDefaultsAndCollectionParameters() {
        DomaSqlTemplateParser.ParseResult result = parser.parse("""
                select *
                from public.orders
                where status = /* status */'ENABLED'
                  and enabled = /* enabled */true
                  and amount >= /* minAmount */12.5
                  and id in /* orderIds */(1, 2, 3)
                """);

        assertThat(result.params()).hasSize(4);
        assertThat(result.params().get(0).paramName()).isEqualTo("status");
        assertThat(result.params().get(0).defaultValue()).isEqualTo("'ENABLED'");
        assertThat(result.params().get(1).paramName()).isEqualTo("enabled");
        assertThat(result.params().get(1).defaultValue()).isEqualTo("true");
        assertThat(result.params().get(2).paramName()).isEqualTo("minAmount");
        assertThat(result.params().get(2).defaultValue()).isEqualTo("12.5");
        assertThat(result.params().get(3).paramName()).isEqualTo("orderIds");
        assertThat(result.params().get(3).collection()).isTrue();
        assertThat(result.params().get(3).defaultValue()).isEqualTo("(1, 2, 3)");
    }

    @Test
    void shouldParseNestedConditionBlocks() {
        DomaSqlTemplateParser.ParseResult result = parser.parse("""
                select *
                from public.orders
                /*%if tenantId != null */
                where tenant_id = /* tenantId */'tenant-a'
                  /*%if status != null */
                  and status = /* status */'ENABLED'
                  /*%end*/
                /*%end*/
                """);

        assertThat(result.params()).hasSize(2);
        assertThat(result.params().get(0).paramName()).isEqualTo("tenantId");
        assertThat(result.params().get(1).paramName()).isEqualTo("status");
    }

    @Test
    void shouldRejectConditionEndWithoutStart() {
        assertThatThrownBy(() -> parser.parse("""
                select *
                from public.orders
                /*%end*/
                where id = /* orderId */1
                """))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("Doma 条件块闭合标签无对应开始标签");
    }
}
