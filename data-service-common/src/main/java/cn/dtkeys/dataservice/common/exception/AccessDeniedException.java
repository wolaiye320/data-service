package cn.dtkeys.dataservice.common.exception;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

public class AccessDeniedException extends DataServiceException {

    public AccessDeniedException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }
}
