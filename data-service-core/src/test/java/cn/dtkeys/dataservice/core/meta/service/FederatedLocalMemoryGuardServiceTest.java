package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.config.DataServiceResourceProtectionProperties;
import cn.dtkeys.dataservice.core.error.ResourceProtectionException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FederatedLocalMemoryGuardServiceTest {

    @Test
    void shouldRejectWhenIntermediateRowsExceedLimit() {
        DataServiceResourceProtectionProperties properties = new DataServiceResourceProtectionProperties();
        properties.setMaxLocalCompIntermediateRows(1);
        FederatedLocalMemoryGuardService service = new FederatedLocalMemoryGuardService(properties);

        assertThatThrownBy(() -> service.validate(
                "FEDERATED_LOCAL_JOIN",
                List.of(Map.of("id", 1), Map.of("id", 2))
        ))
                .isInstanceOf(ResourceProtectionException.class)
                .hasMessage("联邦本地整合中间结果超过系统上限: stage=FEDERATED_LOCAL_JOIN, maxIntermediateRows=1, actualRows=2");
    }
}
