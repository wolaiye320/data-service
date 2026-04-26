package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PublishValidationSnapshotService {

    private final ObjectMapper objectMapper;

    public PublishValidationSnapshotService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 生成发布阶段校验快照。
     */
    public String build(String sqlType,
                        int paramCount,
                        ExpressionSupportService.ExpressionSupportSummary expressionSupportSummary) {
        List<String> checkedRules = new ArrayList<>();
        checkedRules.add("READ_ONLY_SQL");
        checkedRules.add("OBJECT_EXISTENCE");
        checkedRules.add("DRAFT_SNAPSHOT_CONSISTENT");
        checkedRules.add("RESOURCE_LIMITS_COMPLETE");
        if (paramCount > 0) {
            checkedRules.add("PARAM_TYPES_CONFIRMED");
        }
        if ("FEDERATED_SQL".equals(sqlType)) {
            checkedRules.add("FEDERATED_CAPABILITY");
        }
        if (expressionSupportSummary != null && expressionSupportSummary.hasComplexExpression()) {
            checkedRules.add("EXPRESSION_SUPPORT");
        }
        try {
            return objectMapper.writeValueAsString(new PublishValidationSnapshot(
                    "PUBLISH",
                    "PASS",
                    "发布校验通过",
                    checkedRules,
                    expressionSupportSummary
            ));
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "发布校验快照序列化失败", ex);
        }
    }

    public record PublishValidationSnapshot(
            String stage,
            String result,
            String message,
            List<String> checkedRules,
            ExpressionSupportService.ExpressionSupportSummary expressionSupport
    ) {
    }
}
