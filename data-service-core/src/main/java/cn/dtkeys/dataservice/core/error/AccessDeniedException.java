package cn.dtkeys.dataservice.core.error;

public class AccessDeniedException extends DataServiceException {

    public AccessDeniedException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }
}
