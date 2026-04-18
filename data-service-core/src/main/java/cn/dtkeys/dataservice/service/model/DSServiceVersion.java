package cn.dtkeys.dataservice.service.model;

import java.time.LocalDateTime;

public class DSServiceVersion {

    private Long id;
    private Long serviceId;
    private Integer version;
    private String status;
    private String serviceDefinitionJson;
    private String sourceDefinitionJson;
    private String paramDefinitionJson;
    private String fieldDefinitionJson;
    private String sqlDefinitionJson;
    private String createdBy;
    private LocalDateTime createdAt;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getServiceDefinitionJson() {
        return serviceDefinitionJson;
    }

    public void setServiceDefinitionJson(String serviceDefinitionJson) {
        this.serviceDefinitionJson = serviceDefinitionJson;
    }

    public String getSourceDefinitionJson() {
        return sourceDefinitionJson;
    }

    public void setSourceDefinitionJson(String sourceDefinitionJson) {
        this.sourceDefinitionJson = sourceDefinitionJson;
    }

    public String getParamDefinitionJson() {
        return paramDefinitionJson;
    }

    public void setParamDefinitionJson(String paramDefinitionJson) {
        this.paramDefinitionJson = paramDefinitionJson;
    }

    public String getFieldDefinitionJson() {
        return fieldDefinitionJson;
    }

    public void setFieldDefinitionJson(String fieldDefinitionJson) {
        this.fieldDefinitionJson = fieldDefinitionJson;
    }

    public String getSqlDefinitionJson() {
        return sqlDefinitionJson;
    }

    public void setSqlDefinitionJson(String sqlDefinitionJson) {
        this.sqlDefinitionJson = sqlDefinitionJson;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
