package cn.dtkeys.dataservice.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        DataServiceAccessProperties.class,
        DataServiceSecurityProperties.class,
        DataServiceResourceProtectionProperties.class
})
public class DataServiceCoreConfiguration {
}
