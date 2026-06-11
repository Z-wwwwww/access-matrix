package com.platform.core.infrastructure.config;

import com.platform.core.common.time.AppTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/**
 * Binds {@code app.timezone} ({@code CORE_TIMEZONE} env, default Asia/Tokyo)
 * into {@link AppTime} at startup. An invalid IANA id makes {@link ZoneId#of}
 * throw and the boot fail fast — a wrong business timezone must never start
 * silently (it would shift cron firing, day/month bucketing and numbering
 * dates without any error).
 *
 * <p>Consumers read {@link AppTime#zone()} at call time (never cached at
 * construction), so binding in this bean's constructor is early enough.
 */
@Component
public class AppTimeConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AppTimeConfigurer.class);

    public AppTimeConfigurer(@Value("${app.timezone:Asia/Tokyo}") String zoneId) {
        ZoneId zone = ZoneId.of(zoneId);
        AppTime.configure(zone);
        log.info("[time] business timezone = {} (app.timezone)", zone);
    }
}
