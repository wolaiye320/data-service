package cn.dtkeys.dataservice.query.executor;

import java.util.Map;

public record BoundQuery(String sql, Map<String, Object> params) {
}
