package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SaveValidationSnapshotService {

    private final ObjectMapper objectMapper;

    public SaveValidationSnapshotService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 生成保存阶段校验诊断快照。
     */
    public String build(String sqlType,
                        int paramCount,
                        ExpressionSupportService.ExpressionSupportSummary expressionSupportSummary) {
        List<String> checkedRules = new ArrayList<>();
        checkedRules.add("READ_ONLY_SQL");
        checkedRules.add("DOMA_TEMPLATE");
        if ("SIMPLE_SQL".equals(sqlType)) {
            checkedRules.add("DEFAULT_CONNECTION");
        }
        if ("FEDERATED_SQL".equals(sqlType)) {
            checkedRules.add("FEDERATED_ALIAS");
        }
        checkedRules.add("OBJECT_EXISTENCE");
        if (paramCount > 0) {
            checkedRules.add("PARAM_TYPES_CONFIRMED");
        }
        if (expressionSupportSummary != null && expressionSupportSummary.hasComplexExpression()) {
            checkedRules.add("EXPRESSION_SUPPORT");
        }
        try {
            return objectMapper.writeValueAsString(new SaveValidationSnapshot(
                    "SAVE",
                    "PASS",
                    "保存校验通过",
                    checkedRules,
                    expressionSupportSummary
            ));
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "保存校验快照序列化失败", ex);
        }
    }

    public record SaveValidationSnapshot(
            String stage,
            String result,
            String message,
            List<String> checkedRules,
            ExpressionSupportService.ExpressionSupportSummary expressionSupport
    ) {
    }
}
