package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ReadOnlySqlGuard {

    private static final Pattern LEADING_COMMENT_PATTERN = Pattern.compile("^(\\s|--.*?(\\R|$))+");
    private static final Pattern BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("'([^']|'')*'");
    private static final Pattern PROCEDURE_CALL_PATTERN = Pattern.compile("(?i)\\b(call|execute|exec|begin|declare)\\b");
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "insert", "update", "delete", "merge", "replace",
            "create", "alter", "drop", "truncate", "comment",
            "grant", "revoke", "commit", "rollback", "vacuum",
            "analyze", "copy", "load", "unload"
    );
    private static final Set<String> DANGEROUS_FUNCTIONS = Set.of(
            "sleep", "pg_sleep", "dbms_lock.sleep", "benchmark"
    );

    /**
     * 校验 SQL 仅包含平台允许的只读语法。
     */
    public void validate(String sqlText) {
        if (sqlText == null || sqlText.isBlank()) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "SQL 不能为空");
        }

        String normalized = normalize(sqlText);
        String firstKeyword = firstKeyword(normalized);
        if (!"select".equals(firstKeyword) && !"with".equals(firstKeyword)) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "仅允许 select 或 with 查询语句");
        }
        if (containsMultiStatement(normalized)) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "不允许多语句 SQL");
        }
        if (containsForbiddenKeyword(normalized)) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "SQL 包含不允许的写入或 DDL 语句");
        }
        if (containsProcedureCall(normalized)) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "SQL 不允许存储过程或过程式语句");
        }
        if (containsDangerousFunction(normalized)) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "SQL 包含危险函数调用");
        }
    }

    private String normalize(String sqlText) {
        String noLeadingComments = LEADING_COMMENT_PATTERN.matcher(sqlText).replaceFirst("");
        String withoutBlockComments = BLOCK_COMMENT_PATTERN.matcher(noLeadingComments).replaceAll(" ");
        String withoutStrings = STRING_LITERAL_PATTERN.matcher(withoutBlockComments).replaceAll("''");
        return withoutStrings.trim();
    }

    private String firstKeyword(String normalized) {
        String[] parts = normalized.split("\\s+", 2);
        return parts.length == 0 ? "" : parts[0].toLowerCase(Locale.ROOT);
    }

    private boolean containsMultiStatement(String normalized) {
        return normalized.contains(";");
    }

    private boolean containsForbiddenKeyword(String normalized) {
        String lower = normalized.toLowerCase(Locale.ROOT);
        return FORBIDDEN_KEYWORDS.stream().anyMatch(keyword -> lower.matches("(?s).*\\b" + Pattern.quote(keyword) + "\\b.*"));
    }

    private boolean containsProcedureCall(String normalized) {
        return PROCEDURE_CALL_PATTERN.matcher(normalized).find();
    }

    private boolean containsDangerousFunction(String normalized) {
        String lower = normalized.toLowerCase(Locale.ROOT);
        return DANGEROUS_FUNCTIONS.stream().anyMatch(function -> lower.matches("(?s).*\\b" + Pattern.quote(function) + "\\s*\\(.*"));
    }
}
