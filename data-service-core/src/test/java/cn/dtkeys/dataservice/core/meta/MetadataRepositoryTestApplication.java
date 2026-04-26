package cn.dtkeys.dataservice.core.meta;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "cn.dtkeys.dataservice")
public class MetadataRepositoryTestApplication {
}
