package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.QueryParamValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryParamBindingServiceTest {

    private final QueryParamBindingService service = new QueryParamBindingService(new ObjectMapper());

    @Test
    void shouldConvertScalarAndCollectionParamsBySnapshot() {
        QueryParamBindingService.BoundQueryParams bound = service.bind(
                """
                [
                  {"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"},
                  {"paramName":"orderIds","paramType":"LONG","placeholder":"/* orderIds */","collection":true,"defaultValue":"(1,2)"},
                  {"paramName":"bizDate","paramType":"DATE","placeholder":"/* bizDate */","collection":false,"defaultValue":"'2026-01-01'"},
                  {"paramName":"eventTime","paramType":"LOCAL_DATE_TIME","placeholder":"/* eventTime */","collection":false,"defaultValue":"'2026-01-01T00:00:00'"},
                  {"paramName":"amount","paramType":"DECIMAL","placeholder":"/* amount */","collection":false,"defaultValue":"1.0"}
                ]
                """,
                Map.of(
                        "orderId", "1",
                        "orderIds", List.of("2", 3),
                        "bizDate", "2026-04-25",
                        "eventTime", "2026-04-25T10:15:30",
                        "amount", "88.50"
                )
        );

        assertEquals(1L, bound.params().get("orderId"));
        assertEquals(List.of(2L, 3L), bound.params().get("orderIds"));
        assertEquals(LocalDate.parse("2026-04-25"), bound.params().get("bizDate"));
        assertEquals(LocalDateTime.parse("2026-04-25T10:15:30"), bound.params().get("eventTime"));
        assertEquals(new BigDecimal("88.50"), bound.params().get("amount"));
    }

    @Test
    void shouldReportMissingExtraAndTypeMismatchDiagnostics() {
        QueryParamValidationException ex = assertThrows(
                QueryParamValidationException.class,
                () -> service.bind(
                        """
                        [
                          {"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"},
                          {"paramName":"orderIds","paramType":"LONG","placeholder":"/* orderIds */","collection":true,"defaultValue":"(1,2)"}
                        ]
                        """,
                        Map.of(
                                "orderIds", "bad",
                                "unexpected", 1
                        )
                )
        );

        assertEquals("查询参数校验失败", ex.getMessage());
        assertEquals(3, ex.getDiagnostics().size());
        assertEquals("MISSING_PARAMETER", ex.getDiagnostics().get(0).reasonCode());
        assertEquals("orderId", ex.getDiagnostics().get(0).paramName());
        assertEquals("COLLECTION_MISMATCH", ex.getDiagnostics().get(1).reasonCode());
        assertEquals("orderIds", ex.getDiagnostics().get(1).paramName());
        assertEquals("EXTRA_PARAMETER", ex.getDiagnostics().get(2).reasonCode());
        assertEquals("unexpected", ex.getDiagnostics().get(2).paramName());
        assertInstanceOf(String.class, ex.getDiagnostics().get(1).actualType());
    }

    @Test
    void shouldReportScalarTypeMismatchAndExtraParameter() {
        QueryParamValidationException ex = assertThrows(
                QueryParamValidationException.class,
                () -> service.bind(
                        """
                        [
                          {"paramName":"orderId","paramType":"LONG","placeholder":"/* orderId */","collection":false,"defaultValue":"1"}
                        ]
                        """,
                        Map.of(
                                "orderId", "bad-long",
                                "unexpected", 2
                        )
                )
        );

        assertEquals("查询参数校验失败", ex.getMessage());
        assertEquals(2, ex.getDiagnostics().size());
        assertEquals("TYPE_MISMATCH", ex.getDiagnostics().get(0).reasonCode());
        assertEquals("orderId", ex.getDiagnostics().get(0).paramName());
        assertEquals("EXTRA_PARAMETER", ex.getDiagnostics().get(1).reasonCode());
        assertEquals("unexpected", ex.getDiagnostics().get(1).paramName());
        assertInstanceOf(String.class, ex.getDiagnostics().get(0).actualType());
    }
}
