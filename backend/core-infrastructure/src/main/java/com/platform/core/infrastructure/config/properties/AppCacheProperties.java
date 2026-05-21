package com.platform.core.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "app.cache")
public record AppCacheProperties(String defaultSpec, Map<String, String> specs) {

    public AppCacheProperties {
        if (defaultSpec == null || defaultSpec.isBlank()) {
            defaultSpec = "maximumSize=10000,expireAfterWrite=10m,recordStats";
        }
        if (specs == null) specs = Map.of();
    }
}
