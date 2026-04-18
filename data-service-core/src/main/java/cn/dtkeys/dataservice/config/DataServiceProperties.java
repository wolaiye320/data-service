package cn.dtkeys.dataservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 平台基础配置。
 */
@Validated
@ConfigurationProperties(prefix = "data-service")
public class DataServiceProperties {

    @Valid
    @NotNull
    private final Runtime runtime = new Runtime();

    public Runtime getRuntime() {
        return runtime;
    }

    public static class Runtime {

        @Min(1)
        private int port = 8081;

        @NotBlank
        private String defaultProfile = "local";

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDefaultProfile() {
            return defaultProfile;
        }

        public void setDefaultProfile(String defaultProfile) {
            this.defaultProfile = defaultProfile;
        }
    }
}
