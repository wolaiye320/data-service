package cn.dtkeys.dataservice.web.dto.query;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * 统一查询请求 DTO。
 *
 * @param serviceCode 服务编码
 * @param params 单次查询参数
 * @param batchParams 批量查询参数，当前阶段暂未开放
 * @param context 请求上下文
 */
public record QueryRequest(@NotBlank(message = "serviceCode 不能为空") String serviceCode,
                           Map<String, Object> params,
                           List<Map<String, Object>> batchParams,
                           QueryRequestContext context) {

    public QueryRequest {
        params = params == null ? Map.of() : Map.copyOf(params);
        batchParams = batchParams == null ? List.of() : List.copyOf(batchParams);
    }

    public String operator() {
        return context == null ? null : context.operator();
    }
}
