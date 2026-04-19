package cn.dtkeys.dataservice.query.executor;

import cn.dtkeys.dataservice.common.exception.ParamInvalidException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 渲染 Doma 风格 SQL 模板最小子集。
 */
public final class SqlTemplateRenderer {

    private static final Pattern IF_BLOCK_PATTERN = Pattern.compile("(?s)/\\*%if\\s+(.+?)\\s*\\*/(.*?)/\\*%end\\*/");
    private static final Pattern BIND_VARIABLE_PATTERN =
        Pattern.compile("/\\*\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\*/\\s*(\\([^)]*\\)|[^\\s,)]+)");
    private static final Pattern LEGACY_NAMED_PARAMETER_PATTERN = Pattern.compile("(^|[^:]):([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern PARAM_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private SqlTemplateRenderer() {
    }

    public static RenderedSql render(String sql, Map<String, Object> params) {
        if (sql == null) {
            throw new ParamInvalidException("SQL 不能为空");
        }
        validateTemplateSyntax(sql);
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        String conditionRenderedSql = renderIfBlocks(sql, safeParams);
        LinkedHashSet<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = BIND_VARIABLE_PATTERN.matcher(conditionRenderedSql);
        StringBuilder renderedSql = new StringBuilder();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            placeholders.add(placeholder);
            matcher.appendReplacement(renderedSql, Matcher.quoteReplacement(":" + placeholder));
        }
        matcher.appendTail(renderedSql);
        return new RenderedSql(renderedSql.toString(), Collections.unmodifiableSet(placeholders));
    }

    public static Set<String> extractPlaceholders(String sql) {
        if (sql == null || sql.isBlank()) {
            return Set.of();
        }
        validateTemplateSyntax(sql);
        String normalized = sql
            .replace("/*%end*/", " ")
            .replaceAll("/\\*%if\\s+.+?\\*/", " ");
        LinkedHashSet<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = BIND_VARIABLE_PATTERN.matcher(normalized);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return Collections.unmodifiableSet(placeholders);
    }

    public static void validateTemplateSyntax(String sql) {
        if (sql == null || sql.isBlank()) {
            return;
        }
        Matcher legacyMatcher = LEGACY_NAMED_PARAMETER_PATTERN.matcher(sql);
        if (legacyMatcher.find()) {
            throw new ParamInvalidException("SQL 模板仅支持 Doma 风格参数语法，请使用 /* "
                + legacyMatcher.group(2) + " */0");
        }
    }

    private static String renderIfBlocks(String sql, Map<String, Object> params) {
        Matcher matcher = IF_BLOCK_PATTERN.matcher(sql);
        StringBuilder renderedSql = new StringBuilder();
        int blockCount = 0;
        while (matcher.find()) {
            blockCount++;
            String expression = matcher.group(1);
            String body = matcher.group(2);
            String replacement = evaluateExpression(expression, params) ? body : "";
            matcher.appendReplacement(renderedSql, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(renderedSql);
        if (renderedSql.indexOf("/*%if") >= 0 || renderedSql.indexOf("/*%end*/") >= 0) {
            throw new ParamInvalidException("动态 SQL 条件块不完整");
        }
        if (blockCount > 0 && IF_BLOCK_PATTERN.matcher(renderedSql).find()) {
            throw new ParamInvalidException("动态 SQL 暂不支持嵌套条件块");
        }
        return renderedSql.toString();
    }

    private static boolean evaluateExpression(String expression, Map<String, Object> params) {
        String normalized = expression.trim();
        if (normalized.isEmpty()) {
            throw new ParamInvalidException("动态 SQL 条件表达式不能为空");
        }
        String[] orParts = normalized.split("\\s*\\|\\|\\s*");
        boolean result = false;
        for (String orPart : orParts) {
            boolean andResult = true;
            String[] andParts = orPart.split("\\s*&&\\s*");
            for (String andPart : andParts) {
                andResult = andResult && evaluatePredicate(andPart.trim(), params);
            }
            result = result || andResult;
        }
        return result;
    }

    private static boolean evaluatePredicate(String predicate, Map<String, Object> params) {
        Matcher matcher = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)\\s*(==|!=)\\s*(null|''|\"\")$").matcher(predicate);
        if (matcher.matches()) {
            String paramName = matcher.group(1);
            String operator = matcher.group(2);
            String target = matcher.group(3);
            Object value = params.get(paramName);
            boolean equals = "null".equals(target) ? value == null : isEmpty(value);
            return "==".equals(operator) ? equals : !equals;
        }
        if (PARAM_NAME_PATTERN.matcher(predicate).matches()) {
            return !isEmpty(params.get(predicate));
        }
        throw new ParamInvalidException("不支持的动态 SQL 条件表达式: " + predicate);
    }

    private static boolean isEmpty(Object value) {
        return value == null
            || value instanceof String text && text.isBlank()
            || value instanceof Collection<?> collection && collection.isEmpty();
    }

    public record RenderedSql(String sql, Set<String> placeholders) {
    }
}
