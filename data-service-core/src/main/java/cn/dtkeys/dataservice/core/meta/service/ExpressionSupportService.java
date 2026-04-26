package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 统一判定复杂表达式支持状态，并输出稳定诊断结果。
 */
@Service
public class ExpressionSupportService {

    private static final Pattern CASE_PATTERN = Pattern.compile("(?is)\\bcase\\b.+\\bend\\b");
    private static final Pattern CAST_PATTERN = Pattern.compile("(?is)\\bcast\\s*\\(");
    private static final Pattern COUNT_PATTERN = Pattern.compile("(?is)\\b(count|sum|avg|min|max)\\s*\\(");
    private static final Pattern DATE_PATTERN = Pattern.compile("(?is)\\b(current_timestamp|date_trunc|extract|to_char)\\s*\\(");
    private static final Pattern STRING_PATTERN = Pattern.compile("(?is)\\b(concat|substr|substring|trim|upper|lower)\\s*\\(");

    private final DialectRuleService dialectRuleService;
    private final ObjectMapper objectMapper;

    public ExpressionSupportService(DialectRuleService dialectRuleService, ObjectMapper objectMapper) {
        this.dialectRuleService = dialectRuleService;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验 SQL 中的复杂表达式能力，发现明确不支持场景时直接失败。
     */
    public ExpressionSupportSummary validateOrThrow(String sqlText, DsConnectionRecord connection) {
        ExpressionSupportSummary summary = analyze(sqlText, connection);
        summary.items().stream()
                .filter(item -> item.status() == SupportStatus.UNSUPPORTED)
                .findFirst()
                .ifPresent(item -> {
                    throw new DataServiceException(
                            ErrorCode.INVALID_ARGUMENT,
                            "复杂表达式不受支持: type=%s, detail=%s".formatted(item.expressionType(), item.detail())
                    );
                });
        return summary;
    }

    /**
     * 输出表达式判定摘要，供校验快照与前端诊断使用。
     */
    public ExpressionSupportSummary analyze(String sqlText, DsConnectionRecord connection) {
        List<ExpressionSupportItem> items = new ArrayList<>();
        if (sqlText == null || sqlText.isBlank()) {
            return new ExpressionSupportSummary(List.of(), 0, 0, 0);
        }

        addIfMatched(items, sqlText, CASE_PATTERN, ExpressionType.CASE, SupportStatus.SUPPORTED, "CASE WHEN 表达式");
        addIfMatched(items, sqlText, COUNT_PATTERN, ExpressionType.AGGREGATE, SupportStatus.SUPPORTED, "聚合表达式");
        addIfMatched(items, sqlText, DATE_PATTERN, ExpressionType.DATETIME, SupportStatus.REWRITABLE, "日期时间表达式");
        addIfMatched(items, sqlText, STRING_PATTERN, ExpressionType.STRING, resolveStringStatus(connection), "字符串表达式");

        if (CAST_PATTERN.matcher(sqlText).find()) {
            SupportStatus castStatus = sqlText.toLowerCase(Locale.ROOT).contains("json")
                    ? SupportStatus.UNSUPPORTED
                    : SupportStatus.SUPPORTED;
            String detail = castStatus == SupportStatus.UNSUPPORTED
                    ? "当前仅支持常规标量 CAST，暂不支持 JSON/复杂类型转换"
                    : "CAST 表达式";
            items.add(new ExpressionSupportItem(ExpressionType.CAST, castStatus, detail, connectionCode(connection)));
        }

        long supportedCount = items.stream().filter(item -> item.status() == SupportStatus.SUPPORTED).count();
        long rewritableCount = items.stream().filter(item -> item.status() == SupportStatus.REWRITABLE).count();
        long unsupportedCount = items.stream().filter(item -> item.status() == SupportStatus.UNSUPPORTED).count();
        return new ExpressionSupportSummary(items, (int) supportedCount, (int) rewritableCount, (int) unsupportedCount);
    }

    public String toJson(ExpressionSupportSummary summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "表达式判定摘要序列化失败", ex);
        }
    }

    private void addIfMatched(List<ExpressionSupportItem> items,
                              String sqlText,
                              Pattern pattern,
                              ExpressionType expressionType,
                              SupportStatus status,
                              String detail) {
        if (pattern.matcher(sqlText).find()) {
            items.add(new ExpressionSupportItem(expressionType, status, detail, null));
        }
    }

    private SupportStatus resolveStringStatus(DsConnectionRecord connection) {
        if (connection == null) {
            return SupportStatus.SUPPORTED;
        }
        DialectRuleService.RewriteResult rewriteResult = dialectRuleService.rewriteSql("select concat(a, b) from dual", connection);
        return rewriteResult.effectiveRules().isEmpty() ? SupportStatus.SUPPORTED : SupportStatus.REWRITABLE;
    }

    private String connectionCode(DsConnectionRecord connection) {
        return connection == null ? null : connection.getConnectionCode();
    }

    public enum ExpressionType {
        CASE,
        CAST,
        DATETIME,
        STRING,
        AGGREGATE
    }

    public enum SupportStatus {
        SUPPORTED,
        REWRITABLE,
        UNSUPPORTED
    }

    public record ExpressionSupportItem(
            ExpressionType expressionType,
            SupportStatus status,
            String detail,
            String connectionCode
    ) {
    }

    public record ExpressionSupportSummary(
            List<ExpressionSupportItem> items,
            int supportedCount,
            int rewritableCount,
            int unsupportedCount
    ) {
        public boolean hasComplexExpression() {
            return !items.isEmpty();
        }
    }
}
