package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DomaSqlTemplateParser {

    private static final Pattern PARAM_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> SQL_KEYWORDS = new HashSet<>(List.of(
            "select", "from", "where", "join", "left", "right", "inner", "outer", "full",
            "on", "and", "or", "group", "order", "by", "having", "limit", "offset", "union"
    ));

    private final ObjectMapper objectMapper;

    public DomaSqlTemplateParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 Doma 模板参数和条件块。
     */
    public ParseResult parse(String sqlText) {
        List<ParamSnapshot> params = new ArrayList<>();
        int ifDepth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < sqlText.length() - 1; i++) {
            char current = sqlText.charAt(i);
            char next = sqlText.charAt(i + 1);
            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (current == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote || current != '/' || next != '*') {
                continue;
            }

            int end = sqlText.indexOf("*/", i + 2);
            if (end < 0) {
                throw invalid("Doma 模板注释未正确闭合");
            }

            String comment = sqlText.substring(i + 2, end).trim();
            if (comment.startsWith("%if")) {
                ifDepth++;
                i = end + 1;
                continue;
            }
            if ("%end".equals(comment)) {
                ifDepth--;
                if (ifDepth < 0) {
                    throw invalid("Doma 条件块闭合标签无对应开始标签");
                }
                i = end + 1;
                continue;
            }
            if (!PARAM_NAME_PATTERN.matcher(comment).matches()) {
                throw invalid("不允许普通块注释，请使用 Doma 参数或条件块语法");
            }

            String defaultValue = extractDefaultValue(sqlText, end + 2);
            validateDefaultValue(defaultValue);
            boolean collection = isCollectionParameter(sqlText, i, defaultValue);
            params.add(new ParamSnapshot(comment, "UNKNOWN", "/* " + comment + " */", collection, defaultValue));
            i = end + 1;
        }

        if (ifDepth != 0) {
            throw invalid("Doma 条件块未正确闭合");
        }

        return new ParseResult(params, toJson(params));
    }

    private String extractDefaultValue(String sqlText, int startIndex) {
        int index = skipWhitespace(sqlText, startIndex);
        if (index >= sqlText.length()) {
            return "";
        }
        char first = sqlText.charAt(index);
        if (first == '(') {
            int end = findClosingParenthesis(sqlText, index);
            return sqlText.substring(index, end + 1);
        }
        if (first == '\'' || first == '"') {
            int end = findClosingQuote(sqlText, index, first);
            return sqlText.substring(index, end + 1);
        }
        int end = index;
        while (end < sqlText.length()) {
            char current = sqlText.charAt(end);
            if (Character.isWhitespace(current) || current == ',' || current == ')') {
                break;
            }
            end++;
        }
        return sqlText.substring(index, end);
    }

    private boolean isCollectionParameter(String sqlText, int commentStartIndex, String defaultValue) {
        if (!defaultValue.startsWith("(")) {
            return false;
        }
        String prefix = sqlText.substring(0, commentStartIndex).stripTrailing();
        int lastWhitespace = Math.max(prefix.lastIndexOf(' '), Math.max(prefix.lastIndexOf('\n'), prefix.lastIndexOf('\t')));
        String keyword = prefix.substring(lastWhitespace + 1).toLowerCase(Locale.ROOT);
        return "in".equals(keyword);
    }

    private void validateDefaultValue(String defaultValue) {
        if (defaultValue == null || defaultValue.isBlank()) {
            throw invalid("不允许普通块注释，请使用 Doma 参数或条件块语法");
        }
        String normalized = defaultValue.toLowerCase(Locale.ROOT);
        if (SQL_KEYWORDS.contains(normalized)) {
            throw invalid("不允许普通块注释，请使用 Doma 参数或条件块语法");
        }
    }

    private int skipWhitespace(String sqlText, int index) {
        int result = index;
        while (result < sqlText.length() && Character.isWhitespace(sqlText.charAt(result))) {
            result++;
        }
        return result;
    }

    private int findClosingParenthesis(String sqlText, int start) {
        int depth = 0;
        for (int i = start; i < sqlText.length(); i++) {
            char current = sqlText.charAt(i);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw invalid("Doma 集合参数默认值未正确闭合");
    }

    private int findClosingQuote(String sqlText, int start, char quote) {
        for (int i = start + 1; i < sqlText.length(); i++) {
            if (sqlText.charAt(i) == quote && sqlText.charAt(i - 1) != '\\') {
                return i;
            }
        }
        throw invalid("Doma 参数默认值字符串未正确闭合");
    }

    private String toJson(List<ParamSnapshot> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "参数快照序列化失败", ex);
        }
    }

    private DataServiceException invalid(String message) {
        return new DataServiceException(ErrorCode.INVALID_ARGUMENT, message);
    }

    public record ParseResult(List<ParamSnapshot> params, String paramSnapshotJson) {
    }

    public record ParamSnapshot(String paramName,
                                String paramType,
                                String placeholder,
                                boolean collection,
                                String defaultValue) {
    }
}
