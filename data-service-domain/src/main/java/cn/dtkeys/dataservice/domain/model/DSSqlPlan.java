package cn.dtkeys.dataservice.domain.model;

import java.time.LocalDateTime;

public class DSSqlPlan {

    private Long id;
    private Long serviceId;
    private Integer version;
    private String planStage;
    private String planFormat;
    private String planContent;
    private String stageGraphJson;
    private String pushdownSummary;
    private String fallbackReason;
    private String costSummary;
    private String localExecutionSummary;
    private String datasourceScope;
    private LocalDateTime createdAt;
    private String createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getPlanStage() {
        return planStage;
    }

    public void setPlanStage(String planStage) {
        this.planStage = planStage;
    }

    public String getPlanFormat() {
        return planFormat;
    }

    public void setPlanFormat(String planFormat) {
        this.planFormat = planFormat;
    }

    public String getPlanContent() {
        return planContent;
    }

    public void setPlanContent(String planContent) {
        this.planContent = planContent;
    }

    public String getStageGraphJson() {
        return stageGraphJson;
    }

    public void setStageGraphJson(String stageGraphJson) {
        this.stageGraphJson = stageGraphJson;
    }

    public String getPushdownSummary() {
        return pushdownSummary;
    }

    public void setPushdownSummary(String pushdownSummary) {
        this.pushdownSummary = pushdownSummary;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public String getCostSummary() {
        return costSummary;
    }

    public void setCostSummary(String costSummary) {
        this.costSummary = costSummary;
    }

    public String getLocalExecutionSummary() {
        return localExecutionSummary;
    }

    public void setLocalExecutionSummary(String localExecutionSummary) {
        this.localExecutionSummary = localExecutionSummary;
    }

    public String getDatasourceScope() {
        return datasourceScope;
    }

    public void setDatasourceScope(String datasourceScope) {
        this.datasourceScope = datasourceScope;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
