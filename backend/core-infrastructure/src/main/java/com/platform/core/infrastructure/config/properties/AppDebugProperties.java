package com.platform.core.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.debug")
public record AppDebugProperties(boolean exposeErrorDetails) {

    public AppDebugProperties {
        // default false
    }
}
