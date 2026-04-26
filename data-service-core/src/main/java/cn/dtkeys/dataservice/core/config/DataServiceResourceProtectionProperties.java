package cn.dtkeys.dataservice.core.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "data-service.resource-protection")
public class DataServiceResourceProtectionProperties {

    @Min(1)
    @Max(3600)
    private int queryTimeoutSeconds = 30;

    @Min(1)
    @Max(3600)
    private int federatedQueryTimeoutSeconds = 60;

    @Min(1)
    private int maxBatchSize = 100;

    @Min(1)
    private int maxResultRows = 1000;

    @Min(1)
    private int maxConcurrentQueries = 8;

    @Min(1)
    private int maxLocalCompIntermediateRows = 1000;

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

    public int getMaxLocalCompIntermediateRows() {
        return maxLocalCompIntermediateRows;
    }

    public void setMaxLocalCompIntermediateRows(int maxLocalCompIntermediateRows) {
        this.maxLocalCompIntermediateRows = maxLocalCompIntermediateRows;
    }
}
