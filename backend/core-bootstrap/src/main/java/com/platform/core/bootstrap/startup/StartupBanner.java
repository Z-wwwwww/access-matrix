package com.platform.core.bootstrap.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class StartupBanner {

    private static final Logger log = LoggerFactory.getLogger(StartupBanner.class);

    private final Environment env;

    @Value("${server.port:9135}")
    private int port;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Value("${spring.application.name:core-service}")
    private String appName;

    @Value("${app.security.mode:permit-all}")
    private String securityMode;

    public StartupBanner(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onReady() {
        String profile = String.join(",", Arrays.asList(env.getActiveProfiles()));
        if (profile.isEmpty()) profile = "(default)";

        String base = "http://localhost:" + port + contextPath;
        String banner = """

                ============================================================
                  %s is READY
                ------------------------------------------------------------
                  profile        : %s
                  port           : %d
                  context-path   : %s
                  security.mode  : %s
                ------------------------------------------------------------
                  Endpoints:
                    %s/health
                    %s/swagger-ui.html
                    %s/actuator/health
                    POST %s/auth/login
                    POST %s/auth/refresh
                    POST %s/auth/logout
                    GET  %s/user/me
                ============================================================
                """.formatted(appName.toUpperCase(), profile, port, contextPath, securityMode,
                base, base, base, base, base, base, base);
        log.info(banner);
    }
}
