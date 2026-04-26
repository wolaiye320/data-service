package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.ResourceProtectionException;
import cn.dtkeys.dataservice.core.error.QueryParamValidationException;
import cn.dtkeys.dataservice.core.meta.web.response.QueryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryResponseAssemblerTest {

    private final QueryResponseAssembler assembler = new QueryResponseAssembler();

    @Test
    void shouldBuildFailureMetaForParameterValidation() {
        QueryParamValidationException exception = new QueryParamValidationException(
                "查询参数校验失败",
                List.of(new QueryParamValidationException.ParamDiagnostic(
                        "TYPE_MISMATCH",
                        "orderId",
                        "参数类型不匹配",
                        "LONG",
                        "STRING",
                        false
                ))
        );

        QueryResponse.QueryResultItem item = assembler.failureItemFromParamValidation(1, exception);

        assertThat(item.status()).isEqualTo("FAILURE");
        assertThat(item.meta()).isNotNull();
        assertThat(item.meta().elapsedMs()).isNull();
        assertThat(item.meta().cacheHit()).isFalse();
        assertThat(item.meta().diagnosticSummary())
                .containsEntry("failureStage", "PARAM_VALIDATION")
                .containsEntry("detailCount", 1);
        assertThat(item.error()).isNotNull();
        assertThat(item.error().errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(item.error().details()).hasSize(1);
        assertThat(item.error().details().getFirst().path()).isEqualTo("inputs[1].params.orderId");
    }

    @Test
    void shouldBuildFailureMetaForResourceProtection() {
        ResourceProtectionException exception = ResourceProtectionException.resultRowsExceeded(
                "FEDERATED_EXECUTION",
                1,
                2
        );

        QueryResponse.QueryResultItem item = assembler.failureItemFromBusinessException(0, exception);

        assertThat(item.status()).isEqualTo("FAILURE");
        assertThat(item.meta()).isNotNull();
        assertThat(item.meta().cacheHit()).isFalse();
        assertThat(item.meta().diagnosticSummary())
                .containsEntry("failureStage", "RESULT_GUARD")
                .containsEntry("protectionType", "RESULT_ROWS")
                .containsEntry("guardStage", "FEDERATED_EXECUTION")
                .containsEntry("maxResultRows", 1)
                .containsEntry("actualRows", 2)
                .containsEntry("detailCount", 0);
        assertThat(item.error()).isNotNull();
        assertThat(item.error().errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(item.error().details()).isEmpty();
    }

    @Test
    void shouldBuildFailureMetaForMemoryProtection() {
        ResourceProtectionException exception = new ResourceProtectionException(
                "联邦本地整合中间结果超过系统上限: stage=FEDERATED_LOCAL_JOIN, maxIntermediateRows=1, actualRows=2",
                java.util.Map.of(
                        "failureStage", "MEMORY_GUARD",
                        "protectionType", "LOCAL_INTERMEDIATE_ROWS",
                        "guardStage", "FEDERATED_LOCAL_JOIN",
                        "maxIntermediateRows", 1,
                        "actualRows", 2,
                        "detailCount", 0
                )
        );

        QueryResponse.QueryResultItem item = assembler.failureItemFromBusinessException(0, exception);

        assertThat(item.meta().diagnosticSummary())
                .containsEntry("failureStage", "MEMORY_GUARD")
                .containsEntry("protectionType", "LOCAL_INTERMEDIATE_ROWS")
                .containsEntry("guardStage", "FEDERATED_LOCAL_JOIN")
                .containsEntry("maxIntermediateRows", 1)
                .containsEntry("actualRows", 2)
                .containsEntry("detailCount", 0);
    }
}
