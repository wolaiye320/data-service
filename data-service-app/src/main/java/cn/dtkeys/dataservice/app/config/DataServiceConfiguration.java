package cn.dtkeys.dataservice.app.config;

import cn.dtkeys.dataservice.infrastructure.config.DataServiceProperties;
import cn.dtkeys.dataservice.infrastructure.protection.ResourceProtectionProperties;
import cn.dtkeys.dataservice.infrastructure.security.PlatformAccessProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    DataServiceProperties.class,
    ResourceProtectionProperties.class,
    PlatformAccessProperties.class
})
public class DataServiceConfiguration {
}
