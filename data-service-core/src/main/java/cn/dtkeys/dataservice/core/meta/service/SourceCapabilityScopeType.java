package cn.dtkeys.dataservice.core.meta.service;

/**
 * 数据源能力的生效作用域类型。
 */
public enum SourceCapabilityScopeType {
    GLOBAL,
    SCHEMA,
    TABLE,
    CUSTOM;

    public static SourceCapabilityScopeType fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return GLOBAL;
        }
        return switch (value.trim().toUpperCase()) {
            case "GLOBAL" -> GLOBAL;
            case "SCHEMA" -> SCHEMA;
            case "TABLE" -> TABLE;
            default -> CUSTOM;
        };
    }
}
