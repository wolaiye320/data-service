package cn.dtkeys.dataservice.query.protection;

import cn.dtkeys.dataservice.common.exception.QueryTimeoutException;
import cn.dtkeys.dataservice.common.exception.ResourceLimitExceededException;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统级资源保护配置。
 */
@Validated
@ConfigurationProperties(prefix = "data-service.resource-protection")
public class ResourceProtectionProperties {

    @Min(1)
    private int queryTimeoutSeconds = 30;

    @Min(1)
    private int federatedQueryTimeoutSeconds = 60;

    @Min(1)
    private int maxBatchSize = 100;

    @Min(1)
    private int maxResultRows = 1000;

    @Min(1)
    private int maxConcurrentQueries = 8;

    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public int getFederatedQueryTimeoutSeconds() {
        return federatedQueryTimeoutSeconds;
    }

    public void setFederatedQueryTimeoutSeconds(int federatedQueryTimeoutSeconds) {
        this.federatedQueryTimeoutSeconds = federatedQueryTimeoutSeconds;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public int getMaxResultRows() {
        return maxResultRows;
    }

    public void setMaxResultRows(int maxResultRows) {
        this.maxResultRows = maxResultRows;
    }

    public int getMaxConcurrentQueries() {
        return maxConcurrentQueries;
    }

    public void setMaxConcurrentQueries(int maxConcurrentQueries) {
        this.maxConcurrentQueries = maxConcurrentQueries;
    }

    public void validateBatchSize(int batchSize) {
        if (batchSize > maxBatchSize) {
            throw new ResourceLimitExceededException("批量规模超过系统限制: " + maxBatchSize);
        }
    }

    public void validateResultRows(int resultRows) {
        if (resultRows > maxResultRows) {
            throw new ResourceLimitExceededException("结果集行数超过系统限制: " + maxResultRows);
        }
    }

    public void validateQueryTimeout(int timeoutSeconds) {
        if (timeoutSeconds > queryTimeoutSeconds) {
            throw new QueryTimeoutException("查询超时阈值超过系统限制: " + queryTimeoutSeconds + " 秒");
        }
    }

    public void validateFederatedQueryTimeout(int timeoutSeconds) {
        if (timeoutSeconds > federatedQueryTimeoutSeconds) {
            throw new QueryTimeoutException("联邦查询超时阈值超过系统限制: " + federatedQueryTimeoutSeconds + " 秒");
        }
    }

    public Map<String, Object> toSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("queryTimeoutSeconds", queryTimeoutSeconds);
        summary.put("federatedQueryTimeoutSeconds", federatedQueryTimeoutSeconds);
        summary.put("maxBatchSize", maxBatchSize);
        summary.put("maxResultRows", maxResultRows);
        summary.put("maxConcurrentQueries", maxConcurrentQueries);
        return summary;
    }
}
