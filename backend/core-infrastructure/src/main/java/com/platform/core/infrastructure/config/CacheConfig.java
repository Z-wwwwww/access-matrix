package com.platform.core.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.platform.core.infrastructure.config.properties.AppCacheProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    private final AppCacheProperties props;

    public CacheConfig(AppCacheProperties props) {
        this.props = props;
    }

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        List<CaffeineCache> caches = new ArrayList<>();

        CaffeineSpec defaultSpec = CaffeineSpec.parse(props.defaultSpec());
        for (Map.Entry<String, String> e : props.specs().entrySet()) {
            CaffeineSpec spec = CaffeineSpec.parse(e.getValue());
            caches.add(new CaffeineCache(e.getKey(), Caffeine.from(spec).build()));
        }
        if (caches.isEmpty()) {
            caches.add(new CaffeineCache("default", Caffeine.from(defaultSpec).build()));
        }
        manager.setCaches(caches);
        return manager;
    }
}
