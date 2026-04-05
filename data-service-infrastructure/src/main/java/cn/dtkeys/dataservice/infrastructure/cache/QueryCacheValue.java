package cn.dtkeys.dataservice.infrastructure.cache;

import java.util.List;
import java.util.Map;

public record QueryCacheValue(List<Map<String, Object>> rows,
                              Map<String, Object> meta) {
}
