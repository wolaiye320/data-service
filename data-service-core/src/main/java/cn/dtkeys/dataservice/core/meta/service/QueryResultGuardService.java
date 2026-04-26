package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.ResourceProtectionException;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QueryResultGuardService {

    public void validate(DsServiceRecord service,
                         List<Map<String, Object>> rows,
                         Map<String, Object> diagnosticSummary,
                         String stage) {
        Integer maxResultRows = service.getMaxResultRows();
        if (maxResultRows == null || maxResultRows <= 0) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "正式查询要求服务配置 maxResultRows");
        }
        boolean truncated = diagnosticSummary != null && Boolean.TRUE.equals(diagnosticSummary.get("truncated"));
        if (truncated) {
            throw ResourceProtectionException.resultRowsExceeded(stage, maxResultRows, maxResultRows + 1);
        }
        if (rows != null && rows.size() > maxResultRows) {
            throw ResourceProtectionException.resultRowsExceeded(stage, maxResultRows, rows.size());
        }
    }
}
