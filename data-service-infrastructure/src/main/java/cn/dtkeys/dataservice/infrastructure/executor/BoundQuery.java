package cn.dtkeys.dataservice.infrastructure.executor;

import java.util.Map;

public record BoundQuery(String sql, Map<String, Object> params) {
}
