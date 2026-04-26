package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 过滤条件抽取、下推判定与残余过滤摘要服务。
 */
@Service
public class FilterPushdownService {

    public FilterPushdownSummary analyze(String sqlText,
                                         List<SourceSnapshotItemResponse> sources,
                                         List<SourceCapabilityService.CapabilitySummary> capabilitySummaries) {
        List<FilterCondition> extracted = extract(sqlText, sources);
        List<FilterCondition> pushdown = new ArrayList<>();
        List<FilterCondition> residual = new ArrayList<>();

        for (FilterCondition condition : extracted) {
            boolean supported = capabilitySummaries.stream()
                    .filter(summary -> summary.connectionCode().equals(condition.connectionCode()))
                    .flatMap(summary -> summary.capabilities().stream())
                    .anyMatch(item -> "FILTER_PUSHDOWN".equalsIgnoreCase(item.capabilityCode())
                            && "SUPPORTED".equalsIgnoreCase(item.capabilityValue()));
            if (supported && condition.pushdownEligible()) {
                pushdown.add(condition);
            } else {
                residual.add(condition);
            }
        }
        String rewriteSql = pushdown.isEmpty()
                ? null
                : "PUSHDOWN[" + pushdown.stream().map(FilterCondition::expression).toList() + "]";
        String residualReason = residual.isEmpty()
                ? "All extracted filters are eligible for pushdown"
                : "Residual filters require local evaluation or later failure boundary";
        return new FilterPushdownSummary(extracted, pushdown, residual, rewriteSql, residualReason);
    }

    private List<FilterCondition> extract(String sqlText, List<SourceSnapshotItemResponse> sources) {
        String lower = sqlText == null ? "" : sqlText.toLowerCase(Locale.ROOT);
        int whereIndex = lower.indexOf(" where ");
        if (whereIndex < 0) {
            return List.of();
        }
        String whereClause = sqlText.substring(whereIndex + 7).trim();
        String[] parts = whereClause.split("(?i)\\s+and\\s+");
        List<FilterCondition> conditions = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            SourceSnapshotItemResponse matched = sources.stream()
                    .filter(source -> source.alias() != null && trimmed.toLowerCase(Locale.ROOT).contains(source.alias().toLowerCase(Locale.ROOT) + "."))
                    .findFirst()
                    .orElse(null);
            conditions.add(new FilterCondition(
                    trimmed,
                    matched == null ? null : matched.connectionCode(),
                    matched == null ? null : matched.alias(),
                    !trimmed.toLowerCase(Locale.ROOT).contains(" or ")
            ));
        }
        return conditions;
    }

    public record FilterCondition(
            String expression,
            String connectionCode,
            String alias,
            boolean pushdownEligible
    ) {
    }

    public record FilterPushdownSummary(
            List<FilterCondition> extractedConditions,
            List<FilterCondition> pushdownConditions,
            List<FilterCondition> residualConditions,
            String rewrittenPushdownFragment,
            String residualReason
    ) {
    }
}
