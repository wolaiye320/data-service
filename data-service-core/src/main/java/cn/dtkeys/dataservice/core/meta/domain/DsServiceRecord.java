package cn.dtkeys.dataservice.core.meta.domain;

import java.time.LocalDateTime;

public class DsServiceRecord {

    private Long id;
    private String serviceCode;
    private String serviceName;
    private String sqlType;
    private String defaultConnectionCode;
    private String status;
    private Integer currentVersion;
    private Integer maxBatchSize;
    private Integer maxResultRows;
    private Integer queryTimeoutSeconds;
    private Integer federatedQueryTimeoutSeconds;
    private String tenantId;
    private String remark;
    private Boolean deleted;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getSqlType() { return sqlType; }
    public void setSqlType(String sqlType) { this.sqlType = sqlType; }
    public String getDefaultConnectionCode() { return defaultConnectionCode; }
    public void setDefaultConnectionCode(String defaultConnectionCode) { this.defaultConnectionCode = defaultConnectionCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Integer currentVersion) { this.currentVersion = currentVersion; }
    public Integer getMaxBatchSize() { return maxBatchSize; }
    public void setMaxBatchSize(Integer maxBatchSize) { this.maxBatchSize = maxBatchSize; }
    public Integer getMaxResultRows() { return maxResultRows; }
    public void setMaxResultRows(Integer maxResultRows) { this.maxResultRows = maxResultRows; }
    public Integer getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
    public void setQueryTimeoutSeconds(Integer queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }
    public Integer getFederatedQueryTimeoutSeconds() { return federatedQueryTimeoutSeconds; }
    public void setFederatedQueryTimeoutSeconds(Integer federatedQueryTimeoutSeconds) { this.federatedQueryTimeoutSeconds = federatedQueryTimeoutSeconds; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
