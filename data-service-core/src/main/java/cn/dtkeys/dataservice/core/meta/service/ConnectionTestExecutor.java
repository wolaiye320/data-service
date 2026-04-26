package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.web.request.ConnectionPayload;

public interface ConnectionTestExecutor {

    /**
     * 执行 JDBC 连通性测试。
     */
    void test(ConnectionPayload payload);
}
