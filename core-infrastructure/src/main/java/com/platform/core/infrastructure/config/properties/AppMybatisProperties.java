package com.platform.core.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mybatis")
public record AppMybatisProperties(Tenant tenant) {

    public AppMybatisProperties {
        if (tenant == null) tenant = new Tenant(false);
    }

    public record Tenant(boolean enabled) {}
}
