package cn.dtkeys.dataservice.common.enums;

/**
 * 平台统一错误码。
 */
public enum ErrorCode {

    OK("success"),
    PARAM_INVALID("请求参数不合法"),
    RESOURCE_NOT_FOUND("请求资源不存在"),
    SERVICE_NOT_FOUND("服务不存在"),
    SERVICE_DISABLED("服务不可用"),
    SERVICE_CONFIG_INVALID("服务配置不合法"),
    DATASOURCE_UNAVAILABLE("数据源不可用"),
    FEDERATED_SQL_VALIDATE_FAILED("联邦 SQL 校验失败"),
    QUERY_EXECUTION_FAILED("查询执行失败"),
    RESOURCE_LIMIT_EXCEEDED("触发资源保护限制"),
    QUERY_TIMEOUT("查询执行超时"),
    ACCESS_DENIED("无权访问目标资源"),
    AUDIT_LOG_WRITE_FAILED("审计日志写入失败"),
    AUDIT_LOG_QUERY_FAILED("审计日志查询失败"),
    INTERNAL_ERROR("系统内部错误");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
