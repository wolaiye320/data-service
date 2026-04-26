package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.config.DataServiceResourceProtectionProperties;
import cn.dtkeys.dataservice.core.error.ResourceProtectionException;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FederatedExecutionGuardServiceTest {

    @Test
    void shouldRejectWhenConcurrentFederatedQueriesExceedLimit() {
        DataServiceResourceProtectionProperties properties = new DataServiceResourceProtectionProperties();
        properties.setMaxConcurrentQueries(1);
        FederatedExecutionGuardService service = new FederatedExecutionGuardService(properties);

        try (FederatedExecutionGuardService.GuardPermit ignored = service.acquire()) {
            assertThatThrownBy(service::acquire)
                    .isInstanceOf(ResourceProtectionException.class)
                    .hasMessage("联邦查询并发超过系统上限: maxConcurrentQueries=1");
        }
    }

    @Test
    void shouldRejectWhenFederatedElapsedExceedsTimeout() {
        DataServiceResourceProtectionProperties properties = new DataServiceResourceProtectionProperties();
        properties.setFederatedQueryTimeoutSeconds(1);
        FederatedExecutionGuardService service = new FederatedExecutionGuardService(properties);
        DsServiceRecord record = new DsServiceRecord();
        record.setFederatedQueryTimeoutSeconds(1);

        assertThatThrownBy(() -> service.validateElapsed(record, 1001L))
                .isInstanceOf(ResourceProtectionException.class)
                .hasMessage("查询执行超时: stage=FEDERATED_EXECUTION, timeoutSeconds=1");
    }
}
