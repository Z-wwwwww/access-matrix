package com.platform.core.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        String mode,
        Jwt jwt,
        RateLimit rateLimit,
        Lockout lockout,
        PasswordPolicy passwordPolicy,
        RefreshCookie refreshCookie) {

    public AppSecurityProperties {
        if (mode == null || mode.isBlank()) mode = "permit-all";
        if (jwt == null) jwt = new Jwt(null, "tid", "sub", "preferred_username", "scope");
        if (rateLimit == null) rateLimit = new RateLimit(true, 30, Duration.ofMinutes(1));
        if (lockout == null) lockout = new Lockout(true, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
        if (passwordPolicy == null) passwordPolicy = new PasswordPolicy(
                8, 128, true, true, true, true,
                true, "https://api.pwnedpasswords.com", Duration.ofSeconds(3), true);
        if (refreshCookie == null) refreshCookie = new RefreshCookie("core_refresh", "/api/auth", true, "Strict");
    }

    public record Jwt(
            String secret,
            String tenantClaim,
            String userIdClaim,
            String usernameClaim,
            String authoritiesClaim) {}

    public record RateLimit(boolean enabled, int requestsPerMinute, Duration refillPeriod) {}

    public record Lockout(boolean enabled, int maxFailures, Duration window, Duration lockDuration) {}

    public record PasswordPolicy(
            int minLength, int maxLength,
            boolean requireDigit, boolean requireUpper, boolean requireLower, boolean requireSymbol,
            boolean hibpEnabled, String hibpBaseUrl, Duration hibpTimeout, boolean failOpenOnHibpError) {}

    public record RefreshCookie(String name, String path, boolean secure, String sameSite) {}
}
