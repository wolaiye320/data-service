package cn.dtkeys.dataservice.domain.model;

import java.time.LocalDateTime;

public class DSSourceCapability {

    private Long id;
    private Long connectionId;
    private String dbType;
    private String capabilityCode;
    private String capabilityValue;
    private String capabilityDetailJson;
    private String scope;
    private String scopeValue;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public String getCapabilityCode() {
        return capabilityCode;
    }

    public void setCapabilityCode(String capabilityCode) {
        this.capabilityCode = capabilityCode;
    }

    public String getCapabilityValue() {
        return capabilityValue;
    }

    public void setCapabilityValue(String capabilityValue) {
        this.capabilityValue = capabilityValue;
    }

    public String getCapabilityDetailJson() {
        return capabilityDetailJson;
    }

    public void setCapabilityDetailJson(String capabilityDetailJson) {
        this.capabilityDetailJson = capabilityDetailJson;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getScopeValue() {
        return scopeValue;
    }

    public void setScopeValue(String scopeValue) {
        this.scopeValue = scopeValue;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
