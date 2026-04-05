package cn.dtkeys.dataservice.common.exception;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

public class QueryExecutionException extends DataServiceException {

    public QueryExecutionException(String message) {
        super(ErrorCode.QUERY_EXECUTION_FAILED, message);
    }

    public QueryExecutionException(String message, Throwable cause) {
        super(ErrorCode.QUERY_EXECUTION_FAILED, message, cause);
    }
}
