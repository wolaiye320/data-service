package cn.dtkeys.dataservice.core.meta.web.request;

public interface ConnectionPayload {

    String connectionName();

    String dbType();

    String host();

    Integer port();

    String username();

    String password();

    String databaseName();

    String remark();
}
