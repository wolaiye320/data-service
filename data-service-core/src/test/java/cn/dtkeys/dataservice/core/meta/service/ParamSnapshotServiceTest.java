package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.meta.web.request.ParamDefinitionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParamSnapshotServiceTest {

    private final ParamSnapshotService service = new ParamSnapshotService(new ObjectMapper());
    private final DomaSqlTemplateParser parser = new DomaSqlTemplateParser(new ObjectMapper());

    @Test
    void shouldMergeConfirmedTypesAndRebuildSnapshot() {
        DomaSqlTemplateParser.ParseResult parseResult = parser.parse("""
                select *
                from public.orders
                where id = /* orderId */1
                  and status = /* status */'ENABLED'
                """);

        String confirmedSnapshot = service.mergeConfirmedTypes(
                parseResult,
                List.of(
                        new ParamDefinitionRequest("orderId", "LONG"),
                        new ParamDefinitionRequest("status", "STRING")
                )
        );

        assertThat(confirmedSnapshot).contains("\"paramName\":\"orderId\"");
        assertThat(confirmedSnapshot).contains("\"paramType\":\"LONG\"");
        assertThat(confirmedSnapshot).contains("\"paramName\":\"status\"");
        assertThat(confirmedSnapshot).contains("\"paramType\":\"STRING\"");

        String rebuiltSnapshot = service.rebuildConfirmedSnapshot("""
                select *
                from public.orders
                where status = /* status */'ENABLED'
                  and id = /* orderId */1
                """, confirmedSnapshot);

        assertThat(rebuiltSnapshot).contains("\"paramName\":\"status\"");
        assertThat(rebuiltSnapshot).contains("\"paramType\":\"STRING\"");
        assertThat(rebuiltSnapshot).contains("\"paramName\":\"orderId\"");
        assertThat(rebuiltSnapshot).contains("\"paramType\":\"LONG\"");

        service.validateConfirmed(rebuiltSnapshot);
    }

    @Test
    void shouldRejectUnknownOrUnusedParamDefinitions() {
        DomaSqlTemplateParser.ParseResult parseResult = parser.parse("""
                select *
                from public.orders
                where id = /* orderId */1
                """);

        assertThatThrownBy(() -> service.mergeConfirmedTypes(
                parseResult,
                List.of(new ParamDefinitionRequest("orderId", "UNKNOWN"))
        ))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("SQL 参数类型未确认: orderId");

        assertThatThrownBy(() -> service.mergeConfirmedTypes(
                parseResult,
                List.of(
                        new ParamDefinitionRequest("orderId", "LONG"),
                        new ParamDefinitionRequest("unusedParam", "STRING")
                )
        ))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("参数类型确认包含未使用的 SQL 参数: unusedParam");
    }
}
