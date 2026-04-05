package cn.dtkeys.dataservice.common.exception;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

public class QueryTimeoutException extends DataServiceException {

    public QueryTimeoutException(String message) {
        super(ErrorCode.QUERY_TIMEOUT, message);
    }
}
