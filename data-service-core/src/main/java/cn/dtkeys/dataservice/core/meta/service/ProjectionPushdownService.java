package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.service.SqlFieldSnapshotService.FieldSnapshot;
import cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 投影字段裁剪、投影下推与字段映射一致性摘要服务。
 */
@Service
public class ProjectionPushdownService {

    public ProjectionPushdownSummary analyze(List<FieldSnapshot> fieldSnapshots,
                                             List<SourceSnapshotItemResponse> sources,
                                             List<SourceCapabilityService.CapabilitySummary> capabilitySummaries) {
        List<ProjectionField> requestedFields = normalizeRequestedFields(fieldSnapshots);
        List<ProjectionField> pushdownFields = new ArrayList<>();
        List<ProjectionField> residualFields = new ArrayList<>();
        for (ProjectionField field : requestedFields) {
            if (supportsProjectPushdown(field, sources, capabilitySummaries)) {
                pushdownFields.add(field);
            } else {
                residualFields.add(field);
            }
        }
        String rewrittenProjectionFragment = pushdownFields.isEmpty()
                ? null
                : "PROJECT[" + pushdownFields.stream().map(ProjectionField::sourceExpression).toList() + "]";
        FieldMappingSummary fieldMapping = buildFieldMappingSummary(requestedFields, pushdownFields, residualFields);
        return new ProjectionPushdownSummary(
                requestedFields,
                pushdownFields,
                residualFields,
                rewrittenProjectionFragment,
                fieldMapping
        );
    }

    private List<ProjectionField> normalizeRequestedFields(List<FieldSnapshot> fieldSnapshots) {
        if (fieldSnapshots == null || fieldSnapshots.isEmpty()) {
            return List.of();
        }
        return fieldSnapshots.stream()
                .sorted(java.util.Comparator.comparingInt(FieldSnapshot::sortOrder))
                .map(field -> new ProjectionField(
                        field.fieldName(),
                        field.expression(),
                        field.sortOrder(),
                        isPushdownEligible(field.expression())
                ))
                .toList();
    }

    private boolean supportsProjectPushdown(ProjectionField field,
                                            List<SourceSnapshotItemResponse> sources,
                                            List<SourceCapabilityService.CapabilitySummary> capabilitySummaries) {
        if (!field.pushdownEligible()) {
            return false;
        }
        if (sources == null || sources.isEmpty()) {
            return false;
        }
        Set<String> sourceCodes = sources.stream()
                .map(SourceSnapshotItemResponse::connectionCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return sourceCodes.stream().allMatch(connectionCode -> capabilitySummaries.stream()
                .filter(summary -> connectionCode.equals(summary.connectionCode()))
                .flatMap(summary -> summary.capabilities().stream())
                .anyMatch(item -> "PROJECT_PUSHDOWN".equalsIgnoreCase(item.capabilityCode())
                        && "SUPPORTED".equalsIgnoreCase(item.capabilityValue())));
    }

    private boolean isPushdownEligible(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String trimmed = expression.trim();
        if ("*".equals(trimmed) || trimmed.endsWith(".*")) {
            return true;
        }
        return trimmed.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    }

    private FieldMappingSummary buildFieldMappingSummary(List<ProjectionField> requestedFields,
                                                         List<ProjectionField> pushdownFields,
                                                         List<ProjectionField> residualFields) {
        List<String> outputFields = requestedFields.stream().map(ProjectionField::outputField).toList();
        boolean consistent = outputFields.size() == new LinkedHashSet<>(outputFields).size();
        String detail = consistent
                ? "字段顺序与别名可按快照稳定映射"
                : "字段快照存在重复输出名称，需本地映射兜底前先修正";
        return new FieldMappingSummary(
                outputFields,
                pushdownFields.stream().map(ProjectionField::outputField).toList(),
                residualFields.stream().map(ProjectionField::outputField).toList(),
                consistent,
                detail
        );
    }

    public record ProjectionField(
            String outputField,
            String sourceExpression,
            int sortOrder,
            boolean pushdownEligible
    ) {
    }

    public record FieldMappingSummary(
            List<String> outputFields,
            List<String> pushdownOutputFields,
            List<String> residualOutputFields,
            boolean consistent,
            String detail
    ) {
    }

    public record ProjectionPushdownSummary(
            List<ProjectionField> requestedFields,
            List<ProjectionField> pushdownFields,
            List<ProjectionField> residualFields,
            String rewrittenProjectionFragment,
            FieldMappingSummary fieldMapping
    ) {
        public boolean applied() {
            return !pushdownFields.isEmpty();
        }
    }
}
