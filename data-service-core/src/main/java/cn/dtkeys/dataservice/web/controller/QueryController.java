package cn.dtkeys.dataservice.web.controller;

import cn.dtkeys.dataservice.query.QueryApplicationService;
import cn.dtkeys.dataservice.query.QueryExecutionCommand;
import cn.dtkeys.dataservice.query.QueryExecutionResult;
import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.security.PermissionCode;
import cn.dtkeys.dataservice.security.RequirePermission;
import cn.dtkeys.dataservice.web.dto.query.QueryRequest;
import cn.dtkeys.dataservice.web.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外暴露统一查询入口。
 */
@Validated
@RestController
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/data-services")
@RequirePermission(PermissionCode.DATA_SERVICE_QUERY)
public class QueryController {

    private final QueryApplicationService queryApplicationService;

    public QueryController(QueryApplicationService queryApplicationService) {
        this.queryApplicationService = queryApplicationService;
    }

    /**
     * 执行单次统一查询。
     *
     * @param request 查询请求
     * @return 统一响应
     */
    @PostMapping("/query")
    public ApiResponse<Object> query(@Valid @RequestBody QueryRequest request) {
        if (request.params().isEmpty() && request.batchParams().isEmpty()) {
            throw new ParamInvalidException("params 与 batchParams 不能同时为空");
        }

        QueryExecutionResult result = queryApplicationService.execute(
            new QueryExecutionCommand(request.serviceCode(), request.params(), request.batchParams(), request.operator()));
        Object responseData = result.batch() ? result.batchResults() : result.rows();
        return ApiResponse.success(responseData, result.meta());
    }
}
