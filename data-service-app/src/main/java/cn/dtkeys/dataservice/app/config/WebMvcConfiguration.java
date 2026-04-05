package cn.dtkeys.dataservice.app.config;

import cn.dtkeys.dataservice.interfaces.security.PlatformPermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final PlatformPermissionInterceptor platformPermissionInterceptor;

    public WebMvcConfiguration(PlatformPermissionInterceptor platformPermissionInterceptor) {
        this.platformPermissionInterceptor = platformPermissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(platformPermissionInterceptor);
    }
}
