package cn.dtkeys.dataservice.query;

import java.util.List;
import java.util.Map;

/**
 * 统一查询执行结果。
 *
 * @param rows 返回数据
 * @param batch 批量模式标识
 * @param batchResults 批量结果与输入项对应关系
 * @param meta 运行元信息
 */
public record QueryExecutionResult(List<Map<String, Object>> rows,
                                   boolean batch,
                                   List<Map<String, Object>> batchResults,
                                   Map<String, Object> meta) {
}
