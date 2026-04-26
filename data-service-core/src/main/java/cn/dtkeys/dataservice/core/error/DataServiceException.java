package cn.dtkeys.dataservice.core.error;

public class DataServiceException extends RuntimeException {

    private final ErrorCode errorCode;

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
