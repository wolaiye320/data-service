package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.common.exception.QueryExecutionException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PredefinedJoinResultAssembler {

    public List<Map<String, Object>> assemble(List<Map<String, Object>> parentRows,
                                              List<Map<String, Object>> childRows,
                                              PredefinedJoinConfig joinConfig) {
        Map<Object, List<Map<String, Object>>> childRowsByJoinValue = childRows.stream()
            .collect(Collectors.groupingBy(row -> row.get(joinConfig.childJoinField())));

        List<Map<String, Object>> mergedRows = new ArrayList<>();
        for (Map<String, Object> parentRow : parentRows) {
            Object joinValue = parentRow.get(joinConfig.parentJoinField());
            if (joinValue == null) {
                if ("INNER".equals(joinConfig.joinType())) {
                    continue;
                }
                mergedRows.add(parentRow);
                continue;
            }
            List<Map<String, Object>> matchedChildren = childRowsByJoinValue.get(joinValue);
            if (matchedChildren == null || matchedChildren.isEmpty()) {
                if ("INNER".equals(joinConfig.joinType())) {
                    continue;
                }
                mergedRows.add(parentRow);
                continue;
            }
            for (Map<String, Object> childRow : matchedChildren) {
                LinkedHashMap<String, Object> mergedRow = new LinkedHashMap<>(parentRow);
                childRow.forEach((key, value) -> {
                    if (joinConfig.childJoinField().equals(key) && parentRow.containsKey(joinConfig.parentJoinField())) {
                        return;
                    }
                    if (mergedRow.containsKey(key) && !joinConfig.childJoinField().equals(key)) {
                        throw new QueryExecutionException("跨库整合后存在重复输出字段: " + key);
                    }
                    mergedRow.putIfAbsent(key, value);
                });
                mergedRows.add(mergedRow);
            }
        }
        return List.copyOf(mergedRows);
    }
}
