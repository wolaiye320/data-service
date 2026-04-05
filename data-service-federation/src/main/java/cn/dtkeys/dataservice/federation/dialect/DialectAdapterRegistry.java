package cn.dtkeys.dataservice.federation.dialect;

import java.util.Locale;
import java.util.regex.Pattern;

public class DialectAdapterRegistry {

    public String adaptLimit(String sourceType, String sql, int limit) {
        String normalized = sourceType == null ? "GENERIC" : sourceType.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ORACLE" -> "SELECT * FROM (" + sql + ") WHERE ROWNUM <= " + limit;
            default -> sql + " LIMIT " + limit;
        };
    }

    public String adaptSql(String sourceType, String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql 不能为空");
        }
        String normalized = normalize(sourceType);
        String adapted = sql;
        adapted = adaptConcat(normalized, adapted);
        adapted = adaptNullFunction(normalized, adapted);
        adapted = adaptCurrentTimestamp(normalized, adapted);
        rejectUnsupportedExpressions(normalized, adapted);
        return adapted;
    }

    public String adaptExpression(String sourceType, String expression) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        String normalized = normalize(sourceType);
        String adapted = adaptConcat(normalized, expression);
        adapted = adaptNullFunction(normalized, adapted);
        adapted = adaptCurrentTimestamp(normalized, adapted);
        rejectUnsupportedExpressions(normalized, adapted);
        return adapted;
    }

    private String adaptConcat(String sourceType, String sql) {
        if ("ORACLE".equals(sourceType)) {
            return sql.replace("CONCAT(", "CONCAT(");
        }
        if ("MYSQL".equals(sourceType)) {
            return sql.replaceAll("([A-Za-z0-9_\\.]+)\\s*\\|\\|\\s*([A-Za-z0-9_\\.]+)", "CONCAT($1, $2)");
        }
        return sql;
    }

    private String adaptNullFunction(String sourceType, String sql) {
        if ("ORACLE".equals(sourceType)) {
            return sql.replaceAll("(?i)COALESCE\\(", "NVL(");
        }
        if ("MYSQL".equals(sourceType)) {
            return sql.replaceAll("(?i)NVL\\(", "IFNULL(");
        }
        return sql;
    }

    private String adaptCurrentTimestamp(String sourceType, String sql) {
        if ("MYSQL".equals(sourceType)) {
            return sql.replaceAll("(?i)CURRENT_TIMESTAMP\\b", "CURRENT_TIMESTAMP()");
        }
        return sql;
    }

    private void rejectUnsupportedExpressions(String sourceType, String sql) {
        if (Pattern.compile("(?i)CUSTOM_FUNC\\s*\\(").matcher(sql).find()) {
            throw new UnsupportedOperationException("sourceType=" + sourceType + " 不支持 CUSTOM_FUNC");
        }
    }

    private String normalize(String sourceType) {
        return sourceType == null ? "GENERIC" : sourceType.toUpperCase(Locale.ROOT);
    }
}
