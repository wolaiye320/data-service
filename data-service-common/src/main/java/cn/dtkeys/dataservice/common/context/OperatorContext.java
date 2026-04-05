package cn.dtkeys.dataservice.common.context;

import java.util.Optional;

/**
 * 统一维护当前请求操作者上下文。
 */
public final class OperatorContext {

    private static final ThreadLocal<String> OPERATOR_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_IP_HOLDER = new ThreadLocal<>();

    private OperatorContext() {
    }

    public static void setOperator(String operator) {
        OPERATOR_HOLDER.set(operator);
    }

    public static Optional<String> getOperator() {
        return Optional.ofNullable(OPERATOR_HOLDER.get());
    }

    public static void setRole(String role) {
        ROLE_HOLDER.set(role);
    }

    public static Optional<String> getRole() {
        return Optional.ofNullable(ROLE_HOLDER.get());
    }

    public static void setRequestIp(String requestIp) {
        REQUEST_IP_HOLDER.set(requestIp);
    }

    public static Optional<String> getRequestIp() {
        return Optional.ofNullable(REQUEST_IP_HOLDER.get());
    }

    public static void clear() {
        OPERATOR_HOLDER.remove();
        ROLE_HOLDER.remove();
        REQUEST_IP_HOLDER.remove();
    }
}
