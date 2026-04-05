package cn.dtkeys.dataservice.common.exception;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

public class ServiceConfigInvalidException extends DataServiceException {

    public ServiceConfigInvalidException(String message) {
        super(ErrorCode.SERVICE_CONFIG_INVALID, message);
    }
}
