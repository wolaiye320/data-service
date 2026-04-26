package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一计划快照生成与解析服务。
 */
@Service
public class PlanSnapshotService {

    private final ObjectMapper objectMapper;

    public PlanSnapshotService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(PlanSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "计划快照序列化失败", ex);
        }
    }

    public PlanSnapshot fromJson(String planSnapshotJson) {
        if (planSnapshotJson == null || planSnapshotJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(planSnapshotJson, PlanSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "计划快照反序列化失败", ex);
        }
    }

    public record PlanSnapshot(
            String stage,
            String sqlType,
            int sourceCount,
            int paramCount,
            List<String> stages,
            List<SourceCapabilityService.CapabilitySummary> capabilitySummaries,
            LogicalPlanService.LogicalPlanSummary logicalPlan,
            String diagnosticSummary
    ) {
    }
}
