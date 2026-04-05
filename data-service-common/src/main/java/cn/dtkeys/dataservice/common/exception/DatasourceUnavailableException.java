package cn.dtkeys.dataservice.common.exception;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

public class DatasourceUnavailableException extends DataServiceException {

    public DatasourceUnavailableException(String message) {
        super(ErrorCode.DATASOURCE_UNAVAILABLE, message);
    }

    public DatasourceUnavailableException(String message, Throwable cause) {
        super(ErrorCode.DATASOURCE_UNAVAILABLE, message, cause);
    }
}
