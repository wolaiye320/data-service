package cn.dtkeys.dataservice.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册数据服务核心模块使用的配置属性。
 */
@Configuration
@EnableConfigurationProperties({
        DataServiceAccessProperties.class,
        DataServiceSecurityProperties.class,
        DataServiceResourceProtectionProperties.class
})
public class DataServiceCoreConfiguration {
}
