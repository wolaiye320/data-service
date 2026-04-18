package cn.dtkeys.dataservice.query;

import java.util.List;
import java.util.Map;

/**
 * 统一查询执行命令。
 *
 * @param serviceCode 服务编码
 * @param params 单次查询参数
 * @param batchParams 批量查询参数
 * @param operator 调用方标识
 */
public record QueryExecutionCommand(String serviceCode,
                                    Map<String, Object> params,
                                    List<Map<String, Object>> batchParams,
                                    String operator) {
}
