package cn.dtkeys.dataservice.interfaces.security;

import cn.dtkeys.dataservice.common.context.OperatorContext;
import cn.dtkeys.dataservice.infrastructure.security.PlatformAccessProperties;
import cn.dtkeys.dataservice.infrastructure.security.PlatformPermissionEvaluator;
import cn.dtkeys.dataservice.infrastructure.security.PlatformRole;
import cn.dtkeys.dataservice.infrastructure.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理请求操作者上下文并执行权限校验。
 */
@Component
public class PlatformPermissionInterceptor implements HandlerInterceptor {

    private final PlatformAccessProperties platformAccessProperties;
    private final PlatformPermissionEvaluator platformPermissionEvaluator;

    public PlatformPermissionInterceptor(PlatformAccessProperties platformAccessProperties,
                                         PlatformPermissionEvaluator platformPermissionEvaluator) {
        this.platformAccessProperties = platformAccessProperties;
        this.platformPermissionEvaluator = platformPermissionEvaluator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String operator = resolveOperator(request);
        PlatformRole role = PlatformRole.fromHeader(request.getHeader(platformAccessProperties.getRoleHeader()));
        OperatorContext.setOperator(operator);
        OperatorContext.setRole(role.name());
        OperatorContext.setRequestIp(resolveRequestIp(request));

        if (handler instanceof HandlerMethod handlerMethod) {
            RequirePermission requirePermission = resolvePermission(handlerMethod);
            if (requirePermission != null) {
                platformPermissionEvaluator.check(role, requirePermission.value(), request.getRequestURI());
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        OperatorContext.clear();
    }

    private RequirePermission resolvePermission(HandlerMethod handlerMethod) {
        RequirePermission methodPermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (methodPermission != null) {
            return methodPermission;
        }
        return handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
    }

    private String resolveOperator(HttpServletRequest request) {
        String operator = request.getHeader(platformAccessProperties.getOperatorHeader());
        if (operator == null || operator.isBlank()) {
            return "SYSTEM";
        }
        return operator.trim();
    }

    private String resolveRequestIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int delimiterIndex = forwardedFor.indexOf(',');
            return delimiterIndex >= 0 ? forwardedFor.substring(0, delimiterIndex).trim() : forwardedFor.trim();
        }
        return request.getRemoteAddr();
    }
}
