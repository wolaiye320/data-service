package cn.dtkeys.dataservice.common.exception;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

public class ParamInvalidException extends DataServiceException {

    public ParamInvalidException(String message) {
        super(ErrorCode.PARAM_INVALID, message);
    }
}
