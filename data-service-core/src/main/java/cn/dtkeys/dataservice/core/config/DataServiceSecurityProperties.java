package cn.dtkeys.dataservice.core.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 凭据加解密相关安全配置。
 */
@Validated
@ConfigurationProperties(prefix = "data-service.security")
public class DataServiceSecurityProperties {

    @NotBlank
    private String credentialSecret = "change-me-to-32-chars-minimum-secret";

    public String getCredentialSecret() {
        return credentialSecret;
    }

    public void setCredentialSecret(String credentialSecret) {
        this.credentialSecret = credentialSecret;
    }
}
