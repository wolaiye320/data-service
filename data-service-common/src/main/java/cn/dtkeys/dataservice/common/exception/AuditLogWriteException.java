package cn.dtkeys.dataservice.common.exception;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

public class AuditLogWriteException extends DataServiceException {

    public AuditLogWriteException(String message, Throwable cause) {
        super(ErrorCode.AUDIT_LOG_WRITE_FAILED, message, cause);
    }
}
