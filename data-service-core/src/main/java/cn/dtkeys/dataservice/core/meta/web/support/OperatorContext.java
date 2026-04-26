package cn.dtkeys.dataservice.core.meta.web.support;

public record OperatorContext(
        String operator,
        String operatorRole,
        String requestIp
) {
}
