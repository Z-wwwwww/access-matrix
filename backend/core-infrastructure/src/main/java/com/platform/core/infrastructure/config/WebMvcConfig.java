package com.platform.core.infrastructure.config;

import com.platform.core.infrastructure.config.properties.AppCorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class WebMvcConfig {

    private final AppCorsProperties cors;

    public WebMvcConfig(AppCorsProperties cors) {
        this.cors = cors;
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration cfg = new CorsConfiguration();

        List<String> origins = cors.allowedOrigins();
        boolean wildcard = origins.size() == 1 && "*".equals(origins.get(0));
        if (wildcard) {
            cfg.setAllowedOriginPatterns(List.of("*"));
            cfg.setAllowCredentials(true);
        } else {
            cfg.setAllowedOrigins(origins);
            cfg.setAllowCredentials(true);
        }
        cfg.addAllowedHeader("*");
        cfg.addAllowedMethod("*");
        cfg.addExposedHeader("X-Trace-Id");
        cfg.addExposedHeader("X-RateLimit-Remaining");
        cfg.addExposedHeader("Retry-After");
        cfg.setMaxAge(3600L);

        source.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(source);
    }
}
