package cn.dtkeys.dataservice.core.meta.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成发布阶段的计划快照。
 */
@Service
public class PublishPlanService {

    private final LogicalPlanService logicalPlanService;
    private final PlanSnapshotService planSnapshotService;

    public PublishPlanService(ObjectMapper objectMapper, LogicalPlanService logicalPlanService, PlanSnapshotService planSnapshotService) {
        this.logicalPlanService = logicalPlanService;
        this.planSnapshotService = planSnapshotService;
    }

    /**
     * 生成发布阶段计划快照。
     */
    public String build(String sqlType,
                        String sqlText,
                        String sourceSnapshotJson,
                        String fieldSnapshotJson,
                        int sourceCount,
                        int paramCount,
                        List<SourceCapabilityService.CapabilitySummary> capabilitySummaries) {
        List<String> stages = new ArrayList<>();
        stages.add("PARSE_SQL");
        stages.add("VALIDATE_DRAFT_SNAPSHOT");
        stages.add("VALIDATE_PUBLISH_RULES");
        if ("FEDERATED_SQL".equals(sqlType)) {
            stages.add("VALIDATE_FEDERATED_CAPABILITY");
        }
        stages.add("GENERATE_EXECUTION_PLAN");

        // 发布计划要体现“生成执行计划前做过哪些校验”，供后续版本快照和诊断展示复用。
        LogicalPlanService.LogicalPlanSummary logicalPlan =
                logicalPlanService.build(sqlType, sqlText, sourceSnapshotJson, fieldSnapshotJson, paramCount, capabilitySummaries);
        return planSnapshotService.toJson(new PlanSnapshotService.PlanSnapshot(
                "PUBLISH",
                sqlType,
                sourceCount,
                paramCount,
                stages,
                capabilitySummaries,
                logicalPlan,
                "Publish plan generated with " + logicalPlan.nodes().size() + " logical nodes"
        ));
    }
}
