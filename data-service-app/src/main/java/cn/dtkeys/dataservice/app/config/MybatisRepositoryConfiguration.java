package cn.dtkeys.dataservice.app.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("cn.dtkeys.dataservice.repository")
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
public class MybatisRepositoryConfiguration {
}
