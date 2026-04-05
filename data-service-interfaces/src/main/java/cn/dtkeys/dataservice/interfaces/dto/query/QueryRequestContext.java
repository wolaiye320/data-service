package cn.dtkeys.dataservice.interfaces.dto.query;

/**
 * 统一查询请求上下文。
 *
 * @param traceId 调用链追踪标识
 * @param operator 调用方标识
 */
public record QueryRequestContext(String traceId, String operator) {
}
