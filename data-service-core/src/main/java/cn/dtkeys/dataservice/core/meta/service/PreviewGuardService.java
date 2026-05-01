package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import org.springframework.stereotype.Service;

/**
 * 校验草稿预览执行前的只读与资源保护基线。
 */
@Service
public class PreviewGuardService {

    /**
     * 校验预览执行的资源保护基线。
     */
    public void validate(DsServiceRecord service, String sqlType, String sqlText, ReadOnlySqlGuard readOnlySqlGuard) {
        readOnlySqlGuard.validate(sqlText);
        if (service.getMaxResultRows() == null || service.getMaxResultRows() <= 0) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "预览执行要求服务配置 maxResultRows");
        }
        Integer timeoutSeconds = "FEDERATED_SQL".equals(sqlType)
                ? service.getFederatedQueryTimeoutSeconds()
                : service.getQueryTimeoutSeconds();
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "预览执行要求服务配置有效超时时间");
        }
    }
}
