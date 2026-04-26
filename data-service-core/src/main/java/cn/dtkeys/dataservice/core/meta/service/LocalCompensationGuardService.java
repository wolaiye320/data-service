package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本地补算操作清单、语义与资源守卫。
 */
@Service
public class LocalCompensationGuardService {

    private static final int MAX_SAFE_LOCAL_COMP_RESULT_ROWS = 1000;
    private static final int MAX_SAFE_LOCAL_COMP_SOURCE_COUNT = 3;

    public LocalCompensationSummary analyze(String sqlText,
                                            int sourceCount,
                                            Integer maxResultRows,
                                            FilterPushdownService.FilterPushdownSummary filterPushdownSummary,
                                            ProjectionPushdownService.ProjectionPushdownSummary projectionPushdownSummary) {
        List<LocalCompensationOperation> allowedOperations = new ArrayList<>();
        if (filterPushdownSummary != null && !filterPushdownSummary.residualConditions().isEmpty()) {
            allowedOperations.add(LocalCompensationOperation.RESIDUAL_FILTER);
        }
        if (projectionPushdownSummary != null && !projectionPushdownSummary.residualFields().isEmpty()
                && projectionPushdownSummary.fieldMapping().consistent()) {
            allowedOperations.add(LocalCompensationOperation.RESIDUAL_PROJECT_MAPPING);
        }

        boolean required = !allowedOperations.isEmpty();
        if (!required) {
            return new LocalCompensationSummary(
                    List.of(),
                    false,
                    true,
                    true,
                    null,
                    "No local compensation required"
            );
        }

        if (containsOrderBy(sqlText)) {
            return new LocalCompensationSummary(
                    allowedOperations,
                    true,
                    false,
                    true,
                    LocalCompensationFailureType.SEMANTIC,
                    "当前最小闭环不允许本地补算排序语义"
            );
        }
        if (containsAggregate(sqlText) && projectionPushdownSummary != null
                && !projectionPushdownSummary.residualFields().isEmpty()) {
            return new LocalCompensationSummary(
                    allowedOperations,
                    true,
                    false,
                    true,
                    LocalCompensationFailureType.SEMANTIC,
                    "当前最小闭环不允许本地补算聚合投影语义"
            );
        }
        if (projectionPushdownSummary != null && !projectionPushdownSummary.fieldMapping().consistent()) {
            return new LocalCompensationSummary(
                    allowedOperations,
                    true,
                    false,
                    true,
                    LocalCompensationFailureType.SEMANTIC,
                    projectionPushdownSummary.fieldMapping().detail()
            );
        }
        if (maxResultRows == null || maxResultRows > MAX_SAFE_LOCAL_COMP_RESULT_ROWS) {
            return new LocalCompensationSummary(
                    allowedOperations,
                    true,
                    true,
                    false,
                    LocalCompensationFailureType.RESOURCE,
                    "本地补算要求 maxResultRows <= " + MAX_SAFE_LOCAL_COMP_RESULT_ROWS
            );
        }
        if (sourceCount > MAX_SAFE_LOCAL_COMP_SOURCE_COUNT) {
            return new LocalCompensationSummary(
                    allowedOperations,
                    true,
                    true,
                    false,
                    LocalCompensationFailureType.RESOURCE,
                    "本地补算仅允许最多 " + MAX_SAFE_LOCAL_COMP_SOURCE_COUNT + " 个来源参与"
            );
        }
        return new LocalCompensationSummary(
                allowedOperations,
                true,
                true,
                true,
                null,
                "本地补算操作在当前资源阈值内可继续"
        );
    }

    public void validateOrThrow(String sqlText,
                                int sourceCount,
                                Integer maxResultRows,
                                FilterPushdownService.FilterPushdownSummary filterPushdownSummary,
                                ProjectionPushdownService.ProjectionPushdownSummary projectionPushdownSummary) {
        LocalCompensationSummary summary =
                analyze(sqlText, sourceCount, maxResultRows, filterPushdownSummary, projectionPushdownSummary);
        if (!summary.required()) {
            return;
        }
        if (!summary.semanticSafe()) {
            throw new DataServiceException(
                    ErrorCode.INVALID_ARGUMENT,
                    "本地补算校验失败: type=%s, detail=%s".formatted(summary.failureType(), summary.detail())
            );
        }
        if (!summary.resourceSafe()) {
            throw new DataServiceException(
                    ErrorCode.INVALID_ARGUMENT,
                    "本地补算校验失败: type=%s, detail=%s".formatted(summary.failureType(), summary.detail())
            );
        }
    }

    private boolean containsOrderBy(String sqlText) {
        return sqlText != null && sqlText.toLowerCase(Locale.ROOT).contains(" order by ");
    }

    private boolean containsAggregate(String sqlText) {
        String lower = sqlText == null ? "" : sqlText.toLowerCase(Locale.ROOT);
        return lower.contains("count(") || lower.contains("sum(") || lower.contains("avg(")
                || lower.contains("min(") || lower.contains("max(");
    }

    public enum LocalCompensationOperation {
        RESIDUAL_FILTER,
        RESIDUAL_PROJECT_MAPPING
    }

    public enum LocalCompensationFailureType {
        SEMANTIC,
        RESOURCE
    }

    public record LocalCompensationSummary(
            List<LocalCompensationOperation> allowedOperations,
            boolean required,
            boolean semanticSafe,
            boolean resourceSafe,
            LocalCompensationFailureType failureType,
            String detail
    ) {
    }
}
