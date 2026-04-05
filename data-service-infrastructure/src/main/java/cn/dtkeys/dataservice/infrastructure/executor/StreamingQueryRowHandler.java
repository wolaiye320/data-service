package cn.dtkeys.dataservice.infrastructure.executor;

import java.util.Map;

@FunctionalInterface
public interface StreamingQueryRowHandler {

    void handleRow(Map<String, Object> row);
}
