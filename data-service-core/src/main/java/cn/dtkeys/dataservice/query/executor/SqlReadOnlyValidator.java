package cn.dtkeys.dataservice.query.executor;

import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 只允许只读 SQL，拦截危险关键字、多语句和注释逃逸。
 */
@Component
public class SqlReadOnlyValidator {

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)--.*?$");
    private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
        "\\b(insert|update|delete|merge|alter|drop|truncate|call|exec|execute|grant|revoke|create|comment|refresh|analyze|vacuum|copy)\\b");

    /**
     * 校验 SQL 是否为只读查询。
     *
     * @param sql SQL 文本
     */
    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new ParamInvalidException("SQL 不能为空");
        }

        String trimmed = sql.trim();
        if (trimmed.contains(";")) {
            throw new ParamInvalidException("SQL 不允许多语句执行");
        }

        String normalized = normalizeSql(trimmed);
        if (!(normalized.startsWith("select ") || normalized.startsWith("with "))) {
            throw new ParamInvalidException("仅允许执行 SELECT/WITH 只读查询");
        }
        if (FORBIDDEN_KEYWORDS.matcher(normalized).find()) {
            throw new ParamInvalidException("SQL 包含非只读危险关键字");
        }
    }

    private String normalizeSql(String sql) {
        String noBlockComment = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        String noLineComment = LINE_COMMENT.matcher(noBlockComment).replaceAll(" ");
        return noLineComment.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
