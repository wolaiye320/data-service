package cn.dtkeys.dataservice.core.meta.domain;

import java.time.LocalDateTime;

public class DsServiceVersionRecord {

    private Long id;
    private Long serviceId;
    private Integer version;
    private String status;
    private String sqlType;
    private String sqlText;
    private String sourceSnapshotJson;
    private String paramSnapshotJson;
    private String fieldSnapshotJson;
    private String validationSnapshotJson;
    private String planSnapshotJson;
    private String requestContextSnapshotJson;
    private LocalDateTime publishedAt;
    private String publishedBy;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSqlType() { return sqlType; }
    public void setSqlType(String sqlType) { this.sqlType = sqlType; }
    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }
    public String getSourceSnapshotJson() { return sourceSnapshotJson; }
    public void setSourceSnapshotJson(String sourceSnapshotJson) { this.sourceSnapshotJson = sourceSnapshotJson; }
    public String getParamSnapshotJson() { return paramSnapshotJson; }
    public void setParamSnapshotJson(String paramSnapshotJson) { this.paramSnapshotJson = paramSnapshotJson; }
    public String getFieldSnapshotJson() { return fieldSnapshotJson; }
    public void setFieldSnapshotJson(String fieldSnapshotJson) { this.fieldSnapshotJson = fieldSnapshotJson; }
    public String getValidationSnapshotJson() { return validationSnapshotJson; }
    public void setValidationSnapshotJson(String validationSnapshotJson) { this.validationSnapshotJson = validationSnapshotJson; }
    public String getPlanSnapshotJson() { return planSnapshotJson; }
    public void setPlanSnapshotJson(String planSnapshotJson) { this.planSnapshotJson = planSnapshotJson; }
    public String getRequestContextSnapshotJson() { return requestContextSnapshotJson; }
    public void setRequestContextSnapshotJson(String requestContextSnapshotJson) { this.requestContextSnapshotJson = requestContextSnapshotJson; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
