package cn.dtkeys.dataservice.common.exception;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

/**
 * 平台统一业务异常。
 */
public class DataServiceException extends RuntimeException {

    private final ErrorCode errorCode;

    public DataServiceException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public DataServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DataServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
