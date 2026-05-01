package cn.dtkeys.dataservice.app.api.query;

import cn.dtkeys.dataservice.app.api.AdminRequestContext;
import cn.dtkeys.dataservice.core.meta.service.QueryService;
import cn.dtkeys.dataservice.core.meta.web.request.QueryRequest;
import cn.dtkeys.dataservice.core.meta.web.response.QueryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外提供统一的数据服务正式查询入口。
 */
@Validated
@RestController
@RequestMapping("/api/data-services")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 执行已发布数据服务的正式查询。
     */
    @PostMapping("/query")
    public QueryResponse query(@Valid @RequestBody QueryRequest request, HttpServletRequest httpServletRequest) {
        String traceId = (String) httpServletRequest.getAttribute(AdminRequestContext.ATTRIBUTE_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            // 管理过滤器未参与时退回请求头，保证查询审计和响应链路仍可复用同一 traceId。
            traceId = httpServletRequest.getHeader(AdminRequestContext.TRACE_ID_HEADER);
        }
        return queryService.execute(request, traceId);
    }
}
