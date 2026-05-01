package cn.dtkeys.dataservice.app.api;

import cn.dtkeys.dataservice.core.config.DataServiceAccessProperties;
import cn.dtkeys.dataservice.core.meta.service.AuditService;
import cn.dtkeys.dataservice.core.meta.web.support.OperatorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 拦截管理端接口并建立管理员身份、操作人上下文与 traceId。
 */
@Component
public class AdminAccessFilter extends OncePerRequestFilter {

    private final DataServiceAccessProperties accessProperties;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public AdminAccessFilter(DataServiceAccessProperties accessProperties,
                             ObjectMapper objectMapper,
                             AuditService auditService) {
        this.accessProperties = accessProperties;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String operator = request.getHeader(accessProperties.getOperatorHeader());
        String role = request.getHeader(accessProperties.getRoleHeader());
        String traceId = request.getHeader(AdminRequestContext.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        if (operator == null || operator.isBlank() || !"ADMIN".equals(role)) {
            // 管理接口拒绝前也要落审计，避免越权访问只在日志之外“悄悄失败”。
            auditService.recordAdminAccessDenied(
                    "ADMIN_ACCESS_DENIED",
                    resolveTargetType(request.getRequestURI()),
                    request.getRequestURI(),
                    "{\"reason\":\"MISSING_OR_INVALID_ADMIN_HEADERS\"}",
                    new OperatorContext(
                            operator == null || operator.isBlank() ? "anonymous" : operator,
                            role == null || role.isBlank() ? "ANONYMOUS" : role,
                            request.getRemoteAddr()
                    ),
                    traceId
            );
            writeAccessDenied(response, traceId);
            return;
        }

        // 统一把操作人和 traceId 放进请求上下文，供控制器、审计和错误响应链路复用。
        request.setAttribute(AdminRequestContext.ATTRIBUTE_TRACE_ID, traceId);
        request.setAttribute(
                AdminRequestContext.ATTRIBUTE_OPERATOR_CONTEXT,
                new OperatorContext(operator, role, request.getRemoteAddr())
        );
        MDC.put("traceId", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void writeAccessDenied(HttpServletResponse response, String traceId) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                new ApiErrorResponse("ACCESS_DENIED", "管理接口需要携带管理员身份请求头", traceId, null)
        );
    }

    private String resolveTargetType(String requestUri) {
        if (requestUri == null) {
            return "ADMIN_API";
        }
        if (requestUri.startsWith("/api/admin/connections")) {
            return "CONNECTION";
        }
        if (requestUri.startsWith("/api/admin/services")) {
            return "SERVICE";
        }
        return "ADMIN_API";
    }
}
