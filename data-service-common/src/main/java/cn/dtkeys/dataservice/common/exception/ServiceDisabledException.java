package cn.dtkeys.dataservice.common.exception;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

public class ServiceDisabledException extends DataServiceException {

    public ServiceDisabledException(String message) {
        super(ErrorCode.SERVICE_DISABLED, message);
    }
}
