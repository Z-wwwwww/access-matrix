package com.platform.core.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.util.TimeZone;

/**
 * Spring Boot 4.0.6 ships BOTH Jackson 2 (com.fasterxml.jackson.*) and Jackson 3 (tools.jackson.*).
 * Jackson 2 is the default for Spring MVC HttpMessageConverter — configured via spring.jackson.* in yml.
 * The Jackson 3 JsonMapper bean below is exposed for explicit consumers (rate-limit filter writes JSON).
 * Jackson 3 disables WRITE_DATES_AS_TIMESTAMPS by default and routes date features through DateTimeFeature.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapper jsonMapper() {
        return JsonMapper.builder()
                .addModule(new BlackbirdModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .defaultTimeZone(TimeZone.getTimeZone("Asia/Tokyo"))
                .build();
    }
}
