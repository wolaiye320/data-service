package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.config.DataServiceResourceProtectionProperties;
import cn.dtkeys.dataservice.core.error.ResourceProtectionException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 限制联邦本地整合阶段的中间结果规模。
 */
@Service
public class FederatedLocalMemoryGuardService {

    private final DataServiceResourceProtectionProperties resourceProtectionProperties;

    public FederatedLocalMemoryGuardService(DataServiceResourceProtectionProperties resourceProtectionProperties) {
        this.resourceProtectionProperties = resourceProtectionProperties;
    }

    /**
     * 校验本地补算中间结果行数是否超出系统阈值。
     */
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
