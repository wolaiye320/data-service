package cn.dtkeys.dataservice.core.error;

public class ResourceConflictException extends DataServiceException {

    public ResourceConflictException(String message) {
        super(ErrorCode.RESOURCE_CONFLICT, message);
    }

    public ResourceConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
