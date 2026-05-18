package com.platform.core.infrastructure.config;

import com.platform.core.infrastructure.config.properties.AppCacheProperties;
import com.platform.core.infrastructure.config.properties.AppCorsProperties;
import com.platform.core.infrastructure.config.properties.AppDebugProperties;
import com.platform.core.infrastructure.config.properties.AppMybatisProperties;
import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AppSecurityProperties.class,
        AppDebugProperties.class,
        AppCorsProperties.class,
        AppCacheProperties.class,
        AppMybatisProperties.class
})
public class PropertiesAutoConfiguration {
}
