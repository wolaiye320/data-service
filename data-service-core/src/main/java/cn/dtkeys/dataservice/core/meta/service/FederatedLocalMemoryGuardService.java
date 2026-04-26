package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.config.DataServiceResourceProtectionProperties;
import cn.dtkeys.dataservice.core.error.ResourceProtectionException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FederatedLocalMemoryGuardService {

    private final DataServiceResourceProtectionProperties resourceProtectionProperties;

    public FederatedLocalMemoryGuardService(DataServiceResourceProtectionProperties resourceProtectionProperties) {
        this.resourceProtectionProperties = resourceProtectionProperties;
    }

    public void validate(String stage, List<?> rows) {
        int actualRows = rows == null ? 0 : rows.size();
        int maxRows = resourceProtectionProperties.getMaxLocalCompIntermediateRows();
        if (actualRows > maxRows) {
            throw new ResourceProtectionException(
                    "联邦本地整合中间结果超过系统上限: stage=" + stage + ", maxIntermediateRows=" + maxRows + ", actualRows=" + actualRows,
                    java.util.Map.of(
                            "failureStage", "MEMORY_GUARD",
                            "protectionType", "LOCAL_INTERMEDIATE_ROWS",
                            "guardStage", stage,
                            "maxIntermediateRows", maxRows,
                            "actualRows", actualRows,
                            "detailCount", 0
                    )
            );
        }
    }
}
