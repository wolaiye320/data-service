package cn.dtkeys.dataservice.common.exception;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

public class ResourceLimitExceededException extends DataServiceException {

    public ResourceLimitExceededException(String message) {
        super(ErrorCode.RESOURCE_LIMIT_EXCEEDED, message);
    }
}
