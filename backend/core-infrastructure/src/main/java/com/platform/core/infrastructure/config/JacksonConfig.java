package com.platform.core.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

/**
 * Spring Boot 4.0.6 ships BOTH Jackson 2 (com.fasterxml.jackson.*) and Jackson 3 (tools.jackson.*).
 * Jackson 2 is the default for Spring MVC HttpMessageConverter — configured via spring.jackson.* in yml.
 * The Jackson 3 JsonMapper bean below serves the explicit (non-MVC) consumers: OpLog payloads
 * (OpLogAspect), domain-event payloads (OutboxEventPublisher), dict i18n JSON, KC realm templates,
 * the rate-limit filter, etc.
 * Jackson 3 disables WRITE_DATES_AS_TIMESTAMPS by default and routes date features through DateTimeFeature.
 *
 * <p>Deliberately NO default time zone: timestamps are {@code OffsetDateTime} instants and always
 * serialize with their own offset — a configured zone would only change the offset notation
 * (+09:00 vs Z) of the same instant, never the meaning (same reasoning as dropping
 * {@code spring.jackson.time-zone}).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapper jsonMapper() {
        return JsonMapper.builder()
                .addModule(new BlackbirdModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }
}
