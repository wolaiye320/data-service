package cn.dtkeys.dataservice.core.meta.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PreviewPlanService {

    private final LogicalPlanService logicalPlanService;
    private final PlanSnapshotService planSnapshotService;

    public PreviewPlanService(ObjectMapper objectMapper, LogicalPlanService logicalPlanService, PlanSnapshotService planSnapshotService) {
        this.logicalPlanService = logicalPlanService;
        this.planSnapshotService = planSnapshotService;
    }

    /**
     * 生成预览执行计划和关键诊断摘要。
     */
    public String build(String sqlType,
                        String sqlText,
                        String sourceSnapshotJson,
                        String fieldSnapshotJson,
                        int paramCount,
                        List<SourceCapabilityService.CapabilitySummary> capabilitySummaries) {
        List<String> stages = new ArrayList<>();
        stages.add("PARSE_SQL");
        stages.add("VALIDATE_READONLY");
        stages.add("VALIDATE_OBJECTS");
        if ("FEDERATED_SQL".equals(sqlType)) {
            stages.add("VALIDATE_FEDERATION");
        }
        stages.add("EXECUTE_PREVIEW");
        LogicalPlanService.LogicalPlanSummary logicalPlan =
                logicalPlanService.build(sqlType, sqlText, sourceSnapshotJson, fieldSnapshotJson, paramCount, capabilitySummaries);
        return planSnapshotService.toJson(new PlanSnapshotService.PlanSnapshot(
                "PREVIEW",
                sqlType,
                logicalPlan.sourceCount(),
                paramCount,
                stages,
                capabilitySummaries,
                logicalPlan,
                "Preview plan generated with " + logicalPlan.nodes().size() + " logical nodes"
        ));
    }
}
