package cn.dtkeys.dataservice.common.exception;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

public class ServiceNotFoundException extends DataServiceException {

    public ServiceNotFoundException(String message) {
        super(ErrorCode.SERVICE_NOT_FOUND, message);
    }
}
