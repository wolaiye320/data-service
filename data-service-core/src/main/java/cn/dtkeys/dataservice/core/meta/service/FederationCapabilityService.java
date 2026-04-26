package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 联邦组合矩阵与能力边界校验服务。
 */
@Service
public class FederationCapabilityService {

    private static final Set<String> SUPPORTED_DB_TYPES = Set.of("POSTGRESQL", "MYSQL", "ORACLE");

    private final ObjectMapper objectMapper;

    public FederationCapabilityService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 发布前执行异构组合与联邦能力边界校验。
     */
    public FederationValidationSummary validateOrThrow(String sqlText,
                                                       List<SourceSnapshotItemResponse> sources,
                                                       List<SourceCapabilityService.CapabilitySummary> capabilitySummaries,
                                                       ExpressionSupportService.ExpressionSupportSummary expressionSummary) {
        FederationValidationSummary summary = analyze(sqlText, sources, capabilitySummaries, expressionSummary);
        summary.items().stream()
                .filter(item -> item.result() == FederationCheckResult.FAIL)
                .findFirst()
                .ifPresent(item -> {
                    throw new DataServiceException(
                            ErrorCode.INVALID_ARGUMENT,
                            "联邦能力校验失败: type=%s, detail=%s".formatted(item.checkType(), item.detail())
                    );
                });
        return summary;
    }

    public String toJson(FederationValidationSummary summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "联邦校验摘要序列化失败", ex);
        }
    }

    private FederationValidationSummary analyze(String sqlText,
                                                List<SourceSnapshotItemResponse> sources,
                                                List<SourceCapabilityService.CapabilitySummary> capabilitySummaries,
                                                ExpressionSupportService.ExpressionSupportSummary expressionSummary) {
        List<FederationValidationItem> items = new ArrayList<>();
        if (sources.size() < 2) {
            return new FederationValidationSummary(items, 0, 0, 0);
        }

        Set<String> dbTypes = new LinkedHashSet<>();
        for (SourceSnapshotItemResponse source : sources) {
            if (source.dbType() != null) {
                dbTypes.add(source.dbType().toUpperCase(Locale.ROOT));
            }
        }

        if (dbTypes.stream().allMatch(SUPPORTED_DB_TYPES::contains)) {
            items.add(new FederationValidationItem(
                    FederationCheckType.COMBINATION,
                    FederationCheckResult.PASS,
                    "数据库组合属于正式支持矩阵",
                    new ArrayList<>(dbTypes)
            ));
        } else {
            items.add(new FederationValidationItem(
                    FederationCheckType.COMBINATION,
                    FederationCheckResult.FAIL,
                    "数据库组合超出正式支持矩阵",
                    new ArrayList<>(dbTypes)
            ));
        }

        validateCapability(items, capabilitySummaries, "FILTER_PUSHDOWN", FederationCheckType.FILTER_PUSHDOWN);
        validateCapability(items, capabilitySummaries, "PROJECT_PUSHDOWN", FederationCheckType.PROJECT_PUSHDOWN);

        if (containsJoin(sqlText)) {
            validateCapability(items, capabilitySummaries, "JOIN_REORDER", FederationCheckType.JOIN_REORDER);
        }
        if (containsAggregate(sqlText)) {
            validateCapability(items, capabilitySummaries, "AGGREGATE_PUSHDOWN", FederationCheckType.AGGREGATE_PUSHDOWN);
        }
        if (expressionSummary != null && expressionSummary.rewritableCount() > 0) {
            validateCapability(items, capabilitySummaries, "FUNCTION_PUSHDOWN", FederationCheckType.FUNCTION_PUSHDOWN);
        }

        if (expressionSummary != null && expressionSummary.unsupportedCount() > 0) {
            items.add(new FederationValidationItem(
                    FederationCheckType.LOCAL_COMPENSATION,
                    FederationCheckResult.FAIL,
                    "存在无法通过方言规则保持语义一致的表达式，禁止本地补算兜底",
                    List.of()
            ));
        } else {
            items.add(new FederationValidationItem(
                    FederationCheckType.LOCAL_COMPENSATION,
                    FederationCheckResult.PASS,
                    "当前场景未触发禁止本地补算边界",
                    List.of()
            ));
        }

        int passCount = (int) items.stream().filter(item -> item.result() == FederationCheckResult.PASS).count();
        int warnCount = (int) items.stream().filter(item -> item.result() == FederationCheckResult.WARN).count();
        int failCount = (int) items.stream().filter(item -> item.result() == FederationCheckResult.FAIL).count();
        return new FederationValidationSummary(items, passCount, warnCount, failCount);
    }

    private void validateCapability(List<FederationValidationItem> items,
                                    List<SourceCapabilityService.CapabilitySummary> capabilitySummaries,
                                    String capabilityCode,
                                    FederationCheckType checkType) {
        List<String> missingConnections = capabilitySummaries.stream()
                .filter(summary -> summary.capabilities().stream()
                        .noneMatch(item -> capabilityCode.equalsIgnoreCase(item.capabilityCode())))
                .map(SourceCapabilityService.CapabilitySummary::connectionCode)
                .toList();
        if (missingConnections.isEmpty()) {
            items.add(new FederationValidationItem(checkType, FederationCheckResult.PASS, "联邦能力满足要求", List.of()));
            return;
        }
        items.add(new FederationValidationItem(
                checkType,
                FederationCheckResult.FAIL,
                "缺少联邦能力配置: capability=%s, connections=%s".formatted(capabilityCode, missingConnections),
                missingConnections
        ));
    }

    private boolean containsJoin(String sqlText) {
        return sqlText != null && sqlText.toLowerCase(Locale.ROOT).contains(" join ");
    }

    private boolean containsAggregate(String sqlText) {
        String lower = sqlText == null ? "" : sqlText.toLowerCase(Locale.ROOT);
        return lower.contains("count(") || lower.contains("sum(") || lower.contains("avg(")
                || lower.contains("min(") || lower.contains("max(");
    }

    public enum FederationCheckType {
        COMBINATION,
        FILTER_PUSHDOWN,
        PROJECT_PUSHDOWN,
        JOIN_REORDER,
        AGGREGATE_PUSHDOWN,
        FUNCTION_PUSHDOWN,
        LOCAL_COMPENSATION
    }

    public enum FederationCheckResult {
        PASS,
        WARN,
        FAIL
    }

    public record FederationValidationItem(
            FederationCheckType checkType,
            FederationCheckResult result,
            String detail,
            List<String> relatedConnections
    ) {
    }

    public record FederationValidationSummary(
            List<FederationValidationItem> items,
            int passCount,
            int warnCount,
            int failCount
    ) {
    }
}
