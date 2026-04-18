package cn.dtkeys.dataservice.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 平台访问头配置。
 */
@Validated
@ConfigurationProperties(prefix = "data-service.access")
public class PlatformAccessProperties {

    @NotBlank
    private String operatorHeader = "X-Operator";

    @NotBlank
    private String roleHeader = "X-Operator-Role";

    public String getOperatorHeader() {
        return operatorHeader;
    }

    public void setOperatorHeader(String operatorHeader) {
        this.operatorHeader = operatorHeader;
    }

    public String getRoleHeader() {
        return roleHeader;
    }

    public void setRoleHeader(String roleHeader) {
        this.roleHeader = roleHeader;
    }
}
