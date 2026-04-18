package cn.dtkeys.dataservice.query.executor;

import java.util.Map;

@FunctionalInterface
public interface StreamingQueryRowHandler {

    void handleRow(Map<String, Object> row);
}
