package cn.dtkeys.dataservice.federation.dialect;

import java.util.Locale;

public class DialectAdapterRegistry {

    public String adaptLimit(String sourceType, String sql, int limit) {
        String normalized = sourceType == null ? "GENERIC" : sourceType.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ORACLE" -> "SELECT * FROM (" + sql + ") WHERE ROWNUM <= " + limit;
            default -> sql + " LIMIT " + limit;
        };
    }
}
