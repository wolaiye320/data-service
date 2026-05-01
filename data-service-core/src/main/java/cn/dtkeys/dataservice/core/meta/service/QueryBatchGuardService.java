package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.ResourceProtectionException;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 校验正式查询的批量输入规模。
 */
@Service
public class QueryBatchGuardService {

    /**
     * 根据服务配置校验批量输入数量。
     */
    public void validate(DsServiceRecord service, List<?> inputs) {
        Integer maxBatchSize = service.getMaxBatchSize();
        if (maxBatchSize == null || maxBatchSize <= 0) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "正式查询要求服务配置 maxBatchSize");
        }
        int actualBatchSize = inputs == null ? 0 : inputs.size();
        if (actualBatchSize > maxBatchSize) {
            throw ResourceProtectionException.batchSizeExceeded(maxBatchSize, actualBatchSize);
        }
    }
}
