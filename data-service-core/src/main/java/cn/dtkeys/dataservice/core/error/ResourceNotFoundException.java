package cn.dtkeys.dataservice.core.error;

public class ResourceNotFoundException extends DataServiceException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
